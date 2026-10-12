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

import java.util.Collection;
import java.util.Map;

import javax.xml.namespace.QName;

import jakarta.xml.bind.JAXBElement;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;


/*
 * Override default styles to recognise JAXBElement as needing introspection
 */
public final class JAXBToStringStyle {

    public static final ToStringStyle MULTI_LINE_STYLE =
        new JAXBToStringStyleImpl(true);

    public static final ToStringStyle SIMPLE_STYLE =
        new JAXBToStringStyleImpl(false);

    public static final ToStringStyle DEFAULT_STYLE =
        new JAXBToStringStyleImpl();
    
    private JAXBToStringStyle() {
        //utility class
    }
}

class JAXBToStringStyleImpl extends ToStringStyle {
    private static final long serialVersionUID = -584903238590491314L;

    JAXBToStringStyleImpl() {
        super();
    }
            
    JAXBToStringStyleImpl(boolean multiLine) {
        super();
        if (multiLine) {
            this.setContentStart("[");
            this.setFieldSeparator(System.lineSeparator() + "  ");
            this.setFieldSeparatorAtStart(true);
            this.setContentEnd(System.lineSeparator() + "]");
        } else {
            // simple
            this.setUseClassName(false);
            this.setUseIdentityHashCode(false);
            this.setUseFieldNames(false);
            this.setContentStart("");
            this.setContentEnd("");
        }
    }
        
    /*
     * Introspect into JAXBElement as a special case as it does not have a
     * toString() and we loose the content
     * 
     * @see org.apache.commons.lang3.builder.ToStringStyle
     */
    @Override
    protected void appendDetail(StringBuffer buffer, String fieldName, Object value) {
        if (value instanceof JAXBElement) {
            buffer.append(ToStringBuilder.reflectionToString(value, this));
        } else {
            appendValue(buffer, value);
        }
    }

    @Override
    protected void appendDetail(StringBuffer buffer, String fieldName, char value) {
        appendEscaped(buffer, String.valueOf(value));
    }

    /*
     * Older commons-lang3 releases append collections and maps via their own
     * toString(), which bypasses both the escaping below and cycle detection.
     * Route each element through appendInternal instead (as commons-lang3
     * 3.21+ does), keeping the AbstractCollection/AbstractMap layout.
     */
    @Override
    protected void appendDetail(StringBuffer buffer, String fieldName, Collection<?> coll) {
        buffer.append('[');
        boolean first = true;
        for (Object item : coll) {
            if (!first) {
                buffer.append(", ");
            }
            first = false;
            appendElement(buffer, fieldName, item);
        }
        buffer.append(']');
    }

    @Override
    protected void appendDetail(StringBuffer buffer, String fieldName, Map<?, ?> map) {
        buffer.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (!first) {
                buffer.append(", ");
            }
            first = false;
            appendElement(buffer, fieldName, e.getKey());
            buffer.append('=');
            appendElement(buffer, fieldName, e.getValue());
        }
        buffer.append('}');
    }

    private void appendElement(StringBuffer buffer, String fieldName, Object item) {
        if (item == null) {
            appendNullText(buffer, fieldName);
        } else {
            appendInternal(buffer, fieldName, item, true);
        }
    }

    /*
     * Text values and XML names (whose namespace URI can carry character
     * references such as &#10;) usually come straight off the wire, so escape
     * them to stop a value from forging extra log lines or terminal escape
     * sequences.
     * Other objects (e.g. nested generated beans) render through their own
     * toString(), which escapes their text fields in turn.
     */
    private static void appendValue(StringBuffer buffer, Object value) {
        if (value instanceof CharSequence || value instanceof Character || value instanceof QName) {
            appendEscaped(buffer, value.toString());
        } else {
            buffer.append(value);
        }
    }

    private static void appendEscaped(StringBuffer buffer, String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
            case '\n':
                buffer.append("\\n");
                break;
            case '\r':
                buffer.append("\\r");
                break;
            case '\t':
                buffer.append("\\t");
                break;
            default:
                if (Character.isISOControl(c) || c == '\u2028' || c == '\u2029') {
                    buffer.append(String.format("\\u%04x", (int)c));
                } else {
                    buffer.append(c);
                }
            }
        }
    }
}
