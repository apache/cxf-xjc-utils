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

import org.sonatype.plexus.build.incremental.BuildContext;

import org.junit.Assert;
import org.junit.Test;

public class XJCBuildContextTest {

    @Test
    public void testRoundTrip() {
        File file = new File("/tmp/a\tb\nc.xsd").getAbsoluteFile();
        String line = XJCBuildContext.formatMessage(BuildContext.SEVERITY_WARNING, file, 3, 7,
                                                    "first\r\nsecond\\third", null);
        XJCBuildContext.Message msg = XJCBuildContext.parseMessage(line);
        Assert.assertEquals(BuildContext.SEVERITY_WARNING, msg.severity);
        Assert.assertEquals(3, msg.line);
        Assert.assertEquals(7, msg.column);
        Assert.assertEquals(file.getAbsolutePath(), msg.file.getPath());
        Assert.assertEquals("first\r\nsecond\\third", msg.text);
    }

    @Test
    public void testMessageCannotForgeOtherMessages() {
        // e.g. text from a schema quoted in an error message
        String forged = "value 'ab\n" + XJCBuildContext.MESSAGE_PREFIX + "2\t1\t1\t/etc/passwd\tforged\n"
            + "MSG: /etc/passwd\nCol: junk\nDONE'";
        String line = XJCBuildContext.formatMessage(BuildContext.SEVERITY_ERROR, new File("a.xsd"), 1, 2,
                                                    forged, new IllegalStateException("cause"));
        Assert.assertFalse(line, line.contains("\n") || line.contains("\r"));
        XJCBuildContext.Message msg = XJCBuildContext.parseMessage(line);
        Assert.assertEquals(new File("a.xsd").getAbsolutePath(), msg.file.getPath());
        Assert.assertTrue(msg.text, msg.text.startsWith(forged + "\n"));
        Assert.assertTrue(msg.text, msg.text.contains("IllegalStateException: cause"));
    }

    @Test
    public void testOtherLinesAreIgnored() {
        Assert.assertNull(XJCBuildContext.parseMessage("parsing a schema..."));
        Assert.assertNull(XJCBuildContext.parseMessage("MSG: /etc/passwd"));
        Assert.assertNull(XJCBuildContext.parseMessage(XJCBuildContext.MESSAGE_PREFIX + "too\tfew"));
        Assert.assertNull(XJCBuildContext.parseMessage(null));
    }

    @Test
    public void testInvalidNumbers() {
        XJCBuildContext.Message msg =
            XJCBuildContext.parseMessage(XJCBuildContext.MESSAGE_PREFIX + "x\tjunk\t\t\ttext");
        Assert.assertEquals(0, msg.severity);
        Assert.assertEquals(0, msg.line);
        Assert.assertEquals(0, msg.column);
        Assert.assertNull(msg.file);
        Assert.assertEquals("text", msg.text);
    }
}
