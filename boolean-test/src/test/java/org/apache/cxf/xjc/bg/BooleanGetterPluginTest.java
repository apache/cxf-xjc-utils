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
package org.apache.cxf.xjc.bg;

import org.apache.cxf.configuration.foo.Foo;
import org.apache.cxf.configuration.foo2.Toggle;

import org.junit.Assert;
import org.junit.Test;

public class BooleanGetterPluginTest {

    @Test
    public void testBooleanGetter() throws Exception {

        Foo foo = new org.apache.cxf.configuration.foo.ObjectFactory().createFoo();

        foo.getBooleanAttr();
    }

    @Test
    public void testExistingGetterIsNotDuplicated() throws Exception {
        // Toggle only compiles if isSetFlag() was not renamed to clash with getSetFlag()
        Toggle toggle = new org.apache.cxf.configuration.foo2.ObjectFactory().createToggle();
        toggle.setFlag(Boolean.TRUE);
        toggle.setSetFlag("text");

        Assert.assertEquals(Boolean.TRUE, toggle.getFlag());
        Assert.assertEquals("text", toggle.getSetFlag());
        Assert.assertTrue(toggle.isSetFlag());
        Assert.assertTrue(toggle.getSetSetFlag());
        Assert.assertEquals(String.class, Toggle.class.getMethod("getSetFlag").getReturnType());
        Assert.assertNotNull(Toggle.class.getMethod("get1St"));
        try {
            Toggle.class.getMethod("isFlag");
            Assert.fail("isFlag() should have been renamed");
        } catch (NoSuchMethodException e) {
            // expected
        }
    }

}
