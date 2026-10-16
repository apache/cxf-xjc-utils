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

package org.apache.cxf.maven_plugin;

import org.junit.Assert;
import org.junit.Test;

public class DoneFileNameTest {

    @Test
    public void testDistinctLocationsGetDistinctMarkers() throws Exception {
        // both used to map to ".src_main_resources_schemas_a_b.xsd.DONE"
        Assert.assertNotEquals(AbstractXSDToJavaMojo.getDoneFileName("src/main/resources/schemas/a/b.xsd"),
                               AbstractXSDToJavaMojo.getDoneFileName("src/main/resources/schemas/a_b.xsd"));
    }

    @Test
    public void testSameLocationGetsSameMarker() throws Exception {
        Assert.assertEquals(AbstractXSDToJavaMojo.getDoneFileName("http://host/a.xsd?x=1&y=2"),
                            AbstractXSDToJavaMojo.getDoneFileName("http://host/a.xsd?x=1&y=2"));
    }

    @Test
    public void testMarkerIsAPlainFileName() throws Exception {
        StringBuilder location = new StringBuilder("jar:file:/c:/x/y.jar!/a/../b.xsd?q=1&r=2");
        for (int i = 0; i < 100; i++) {
            location.append("/segment").append(i);
        }
        String name = AbstractXSDToJavaMojo.getDoneFileName(location.toString());
        Assert.assertTrue(name, name.matches("\\.[0-9a-f]{64}\\.DONE"));
    }
}
