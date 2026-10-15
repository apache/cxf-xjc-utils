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

package org.apache.cxf.xjc.dv;

import org.apache.cxf.configuration.foo_simple.Not;
import org.apache.cxf.configuration.foo_simple.Query;
import org.apache.cxf.configuration.recursive.Base;
import org.apache.cxf.configuration.recursive.Derived;
import org.apache.cxf.configuration.recursive.Holder;
import org.apache.cxf.configuration.recursive.Node;

import org.junit.Assert;
import org.junit.Test;

/**
 * Field initializers must not construct the owning class again, which would
 * recurse until StackOverflowError.
 */
public class RecursiveTypeTest extends Assert {

    @Test
    public void testRequiredMutualReference() {
        // query -> not -> query / not
        assertNull(new Query().getNot());
        assertNull(new Not().getQuery());
        assertNull(new Not().getNot());
    }

    @Test
    public void testOptionalSelfReference() {
        Node node = new Node();
        assertNull(node.getNext());
        assertEquals("x", node.getLabel());
    }

    @Test
    public void testSubclassReference() {
        assertNull(new Base().getDerived());
        assertNull(new Derived().getDerived());
    }

    @Test
    public void testNonRecursiveReferenceIsInitialized() {
        assertNotNull(new Holder().getLeaf());
    }
}
