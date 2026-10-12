/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.cxf.xjc.runtime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;

import jakarta.xml.bind.JAXBElement;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import org.junit.Assert;
import org.junit.Test;

public class JAXBToStringStyleEscapingTest {

    private static final String FORGED = "ok\r\nINFO forged entry\u001b[31m\u2028";
    private static final String ESCAPED = "ok\\r\\nINFO forged entry\\u001b[31m\\u2028";

    static class Bean {
        String name = FORGED;
        char sep = '\n';
        String[] array = {FORGED};
        List<String> list = Arrays.asList("a", FORGED);
        Map<QName, String> otherAttributes = Collections.singletonMap(new QName("attr"), FORGED);
        JAXBElement<String> element = new JAXBElement<>(new QName("el"), String.class, FORGED);
        // namespace URIs can hold control characters via character references
        QName type = new QName(FORGED, "local");
        Map<QName, String> keyed = Collections.singletonMap(new QName(FORGED, "k"), "v");
        JAXBElement<String> named = new JAXBElement<>(new QName(FORGED, "el"), String.class, "t");
        Integer count = 3;
    }

    @Test
    public void testDefaultStyle() {
        assertEscaped(JAXBToStringStyle.DEFAULT_STYLE);
    }

    @Test
    public void testSimpleStyle() {
        assertEscaped(JAXBToStringStyle.SIMPLE_STYLE);
    }

    @Test
    public void testMultiLineStyle() {
        String ts = assertEscaped(JAXBToStringStyle.MULTI_LINE_STYLE);
        // the style's own field separators are still real line breaks
        Assert.assertTrue(ts.contains(System.lineSeparator() + "  name="));
    }

    @Test
    public void testCollectionAndMapLayout() {
        String ts = ToStringBuilder.reflectionToString(new Bean(), JAXBToStringStyle.DEFAULT_STYLE);
        Assert.assertTrue(ts, ts.contains("list=[a, " + ESCAPED + "]"));
        Assert.assertTrue(ts, ts.contains("otherAttributes={attr=" + ESCAPED + "}"));
        Assert.assertTrue(ts, ts.contains("sep=\\n"));
        Assert.assertTrue(ts, ts.contains("count=3"));
        Assert.assertTrue(ts, ts.contains("type={" + ESCAPED + "}local"));
        Assert.assertTrue(ts, ts.contains("keyed={{" + ESCAPED + "}k=v}"));
        Assert.assertTrue(ts, ts.contains("name={" + ESCAPED + "}el"));
    }

    static class Node {
        String id = "n1";
        List<Object> refs = new ArrayList<>();
        Map<String, Object> byId = new HashMap<>();

        @Override
        public String toString() {
            return ToStringBuilder.reflectionToString(this, JAXBToStringStyle.DEFAULT_STYLE);
        }
    }

    @Test
    public void testCyclesInCollectionsAreNotExpanded() {
        // e.g. an IDREF list pointing back at its owner
        Node n = new Node();
        n.refs.add(n);
        n.byId.put("self", n);
        String ts = n.toString();
        Assert.assertEquals(ts, 1, ts.split("id=n1", -1).length - 1);
    }

    @Test
    public void testNullElements() {
        Bean b = new Bean();
        b.list = Arrays.asList(null, "a");
        String ts = ToStringBuilder.reflectionToString(b, JAXBToStringStyle.DEFAULT_STYLE);
        Assert.assertTrue(ts, ts.contains("list=[<null>, a]"));
    }

    @Test
    public void testPlainTextUnchanged() {
        Bean b = new Bean();
        b.name = "caf\u00e9 \"quoted\" back\\slash";
        String ts = ToStringBuilder.reflectionToString(b, JAXBToStringStyle.DEFAULT_STYLE);
        Assert.assertTrue(ts, ts.contains("name=caf\u00e9 \"quoted\" back\\slash,"));
    }

    private static String assertEscaped(ToStringStyle style) {
        String ts = ToStringBuilder.reflectionToString(new Bean(), style);
        String withoutSeparators = ts.replace(System.lineSeparator(), "");
        for (char c : withoutSeparators.toCharArray()) {
            Assert.assertFalse("unescaped control char " + (int)c + " in " + ts,
                               Character.isISOControl(c) || c == '\u2028' || c == '\u2029');
        }
        // name, array, list, map value, JAXBElement value, QName field,
        // QName map key and JAXBElement name
        Assert.assertEquals(ts, 8, ts.split(java.util.regex.Pattern.quote(ESCAPED), -1).length - 1);
        return ts;
    }
}
