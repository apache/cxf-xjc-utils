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
import java.util.IdentityHashMap;
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

    /*
     * The state of the toString() call in progress on this thread, removed once it completes or
     * fails. commons-lang3 only detects cycles (objects on the current path), so an object
     * referenced from several places, e.g. through xs:IDREF, would otherwise be printed once per
     * path, which grows exponentially with the depth of such references.
     */
    private static final ThreadLocal<CallState> STATE = ThreadLocal.withInitial(CallState::new);

    private static final class CallState {
        // objects already printed in detail during the current top-level call
        final Map<Object, Object> rendered = new IdentityHashMap<>();
        // how many field values are being appended, 0 between the fields of the top-level object
        int depth;
    }

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
        
    private static CallState callState() {
        return STATE.get();
    }

    // for tests: whether this thread's state is clear, as it should be between calls
    static boolean isCallStateClear() {
        CallState state = STATE.get();
        STATE.remove();
        return state.depth == 0 && state.rendered.isEmpty();
    }

    @Override
    public void appendStart(StringBuffer buffer, Object object) {
        CallState state = callState();
        if (state.depth == 0) {
            // a new top-level call
            state.rendered.clear();
        }
        if (object != null) {
            state.rendered.put(object, null);
        }
        super.appendStart(buffer, object);
    }

    @Override
    public void appendEnd(StringBuffer buffer, Object object) {
        try {
            super.appendEnd(buffer, object);
        } finally {
            if (callState().depth == 0) {
                STATE.remove();
            }
        }
    }

    // nested toString() calls happen within this, so it tracks whether a call is top-level
    @Override
    protected void appendInternal(StringBuffer buffer, String fieldName, Object value, boolean detail) {
        CallState state = callState();
        state.depth++;
        try {
            super.appendInternal(buffer, fieldName, value, detail);
        } catch (RuntimeException | Error e) {
            if (state.depth == 1) {
                // the top-level toString() call fails, so don't keep its state
                STATE.remove();
            }
            throw e;
        } finally {
            state.depth--;
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
        if (value != null && callState().rendered.containsKey(value)) {
            // already printed in detail, so just identify it as for a cycle
            appendCyclicObject(buffer, fieldName, value);
        } else if (value instanceof JAXBElement) {
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
     * Same as ToStringStyle in commons-lang3 3.21+, which routes each element
     * through appendInternal (and so through the escaping and cycle detection
     * below). Older commons-lang3 releases append coll/map.toString() instead,
     * which bypasses both, so the 3.21 behaviour is copied here.
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
            if (item == null) {
                appendNullText(buffer, fieldName);
            } else {
                appendInternal(buffer, fieldName, item, true);
            }
        }
        buffer.append(']');
    }

    @Override
    protected void appendDetail(StringBuffer buffer, String fieldName, Map<?, ?> map) {
        buffer.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (!first) {
                buffer.append(getArraySeparator());
                buffer.append(' ');
            }
            first = false;
            appendMapItem(buffer, fieldName, e.getKey());
            buffer.append(getFieldNameValueSeparator());
            appendMapItem(buffer, fieldName, e.getValue());
        }
        buffer.append('}');
    }

    private void appendMapItem(StringBuffer buffer, String fieldName, Object item) {
        if (item == null) {
            // what appendInternal(null) gives in 3.21+; older releases throw NPE
            appendDetail(buffer, fieldName, (Object) null);
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
