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
package org.apache.cxf.xjc.javadoc;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PropertyJavadocTest {

    @Test
    public void trimLinesSingleLine() {
        assertEquals("hello", PropertyJavadoc.trimLines("  hello  "));
    }

    @Test
    public void trimLinesMultiline() {
        assertEquals("Multiline documentation of\nattribute",
                PropertyJavadoc.trimLines("\n                    Multiline documentation of\n"
                        + "                    attribute\n                "));
    }

    @Test
    public void trimLinesPreservesInternalSpaces() {
        assertEquals("hello   world", PropertyJavadoc.trimLines("  hello   world  "));
    }

    @Test
    public void trimLinesSkipsBlankLines() {
        assertEquals("line one\nline two", PropertyJavadoc.trimLines("  line one  \n\n  line two  "));
    }

    @Test
    public void trimLinesEmpty() {
        assertEquals("", PropertyJavadoc.trimLines("   \n   \n   "));
    }
}
