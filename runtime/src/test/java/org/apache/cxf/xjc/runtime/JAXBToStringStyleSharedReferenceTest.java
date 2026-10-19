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

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import org.junit.Assert;
import org.junit.Test;

/**
 * An object referenced from several places (e.g. through xs:IDREF) is printed in detail once
 * per toString() call, so output stays linear in the size of the object graph.
 */
public class JAXBToStringStyleSharedReferenceTest {

    static class Node {
        String id;
        Object left;
        Object right;

        Node(String id) {
            this.id = id;
        }

        @Override
        public String toString() {
            return ToStringBuilder.reflectionToString(this, JAXBToStringStyle.DEFAULT_STYLE);
        }
    }

    static class Failing {
        @Override
        public String toString() {
            throw new IllegalStateException("toString failed");
        }
    }

    @Test
    public void testSharedReferenceIsPrintedOnce() {
        Node shared = new Node("shared");
        Node parent = new Node("parent");
        parent.left = shared;
        parent.right = shared;
        String ts = parent.toString();
        Assert.assertEquals(ts, 1, count(ts, "id=shared"));
        // the second reference only identifies the object, as for a cycle
        Assert.assertTrue(ts, ts.contains("right=" + Node.class.getName() + "@"));
        // a separate call prints it in detail again
        Assert.assertTrue(shared.toString().contains("id=shared"));
    }

    @Test
    public void testDeeplySharedReferences() {
        // each level references the level below twice: 2^40 paths without sharing
        Node node = new Node("leaf");
        for (int i = 0; i < 40; i++) {
            Node parent = new Node("n" + i);
            parent.left = node;
            parent.right = node;
            node = parent;
        }
        String ts = node.toString();
        Assert.assertEquals(1, count(ts, "id=leaf"));
        Assert.assertTrue(ts.length() < 10000);
    }

    @Test
    public void testAllStyles() {
        for (ToStringStyle style : new ToStringStyle[] {JAXBToStringStyle.DEFAULT_STYLE,
            JAXBToStringStyle.SIMPLE_STYLE, JAXBToStringStyle.MULTI_LINE_STYLE}) {
            Node shared = new Node("shared");
            Node parent = new Node("parent");
            parent.left = shared;
            parent.right = shared;
            String ts = ToStringBuilder.reflectionToString(parent, style);
            Assert.assertEquals(ts, 1, count(ts, "shared"));
        }
    }

    @Test
    public void testFailedToStringDoesNotAffectLaterCalls() {
        Node shared = new Node("shared");
        Node failing = new Node("failing");
        failing.left = shared;
        failing.right = new Failing();
        try {
            Assert.fail("toString() should have failed: " + failing.toString());
        } catch (IllegalStateException e) {
            // expected
        }
        Node parent = new Node("parent");
        parent.left = shared;
        Assert.assertTrue(parent.toString().contains("id=shared"));
    }

    @Test
    public void testNoStateKeptAfterCalls() {
        Node node = new Node("n");
        node.left = new Failing();
        try {
            Assert.fail(node.toString());
        } catch (IllegalStateException e) {
            // expected
        }
        Assert.assertFalse(JAXBToStringStyleImpl.hasCallState());
        node.left = new Node("ok");
        Assert.assertTrue(node.toString().contains("id=ok"));
        Assert.assertFalse(JAXBToStringStyleImpl.hasCallState());
    }

    private static int count(String s, String part) {
        return s.split(java.util.regex.Pattern.quote(part), -1).length - 1;
    }
}
