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

package org.apache.cxf.xjc.ts;

import com.sun.tools.xjc.BadCommandLineException;
import com.sun.tools.xjc.Options;

import org.junit.Assert;
import org.junit.Test;

public class ToStringPluginTest {

    private static int parse(String arg) throws Exception {
        com.sun.tools.xjc.addon.apache_cxf.ts.ToStringPlugin plugin =
            new com.sun.tools.xjc.addon.apache_cxf.ts.ToStringPlugin();
        return plugin.parseArgument(new Options(), new String[] {arg}, 0);
    }

    @Test
    public void testValidStyles() throws Exception {
        Assert.assertEquals(1, parse("-Xts"));
        Assert.assertEquals(1, parse("-Xts:style:multiline"));
        Assert.assertEquals(1, parse("-Xts:style:simple"));
        Assert.assertEquals(1, parse("-Xts:style:org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE"));
    }

    @Test
    public void testStyleWithoutClassName() throws Exception {
        for (String style : new String[] {"FIELD", ".FIELD", "org.example.Style."}) {
            try {
                parse("-Xts:style:" + style);
                Assert.fail("Expected BadCommandLineException for " + style);
            } catch (BadCommandLineException e) {
                Assert.assertTrue(e.getMessage(), e.getMessage().contains(style));
            }
        }
    }
}
