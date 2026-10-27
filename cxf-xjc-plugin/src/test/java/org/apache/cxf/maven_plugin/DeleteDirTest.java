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

import java.io.File;
import java.nio.file.Files;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DeleteDirTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void testDeletesNestedDirectories() throws Exception {
        File dir = tmp.newFolder("a");
        File nested = new File(dir, "b/c");
        Assert.assertTrue(nested.mkdirs());
        Files.write(new File(nested, "X.java").toPath(), new byte[] {1});
        Files.write(new File(dir, "Y.java").toPath(), new byte[] {1});

        Assert.assertTrue(AbstractXSDToJavaMojo.deleteDir(dir));
        Assert.assertFalse(dir.exists());
    }

    @Test
    public void testMissingDirectory() throws Exception {
        Assert.assertTrue(AbstractXSDToJavaMojo.deleteDir(new File(tmp.getRoot(), "missing")));
    }
}
