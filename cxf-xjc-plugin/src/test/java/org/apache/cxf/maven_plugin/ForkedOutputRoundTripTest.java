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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.maven.plugin.logging.SystemStreamLog;
import org.sonatype.plexus.build.incremental.BuildContext;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Runs XJC as the forked process does and passes its output through the parsing used by
 * AbstractXSDToJavaMojo, checking the messages that reach the build context.
 */
public class ForkedOutputRoundTripTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    static class Recorded {
        final File file;
        final int line;
        final int column;
        final String message;
        final int severity;

        Recorded(File file, int line, int column, String message, int severity) {
            this.file = file;
            this.line = line;
            this.column = column;
            this.message = message;
            this.severity = severity;
        }
    }

    static class RecordingBuildContext extends XJCBuildContext {
        final List<Recorded> messages = new ArrayList<>();

        @Override
        public void addMessage(File file, int line, int column, String message, int severity, Throwable cause) {
            messages.add(new Recorded(file, line, column, message, severity));
        }
    }

    @Test
    public void testErrors() throws Exception {
        File xsd = schema("errors.xsd",
            "  <xs:complexType name='a'><xs:sequence><xs:element name='v' type='t:missing1'/>"
            + "</xs:sequence></xs:complexType>\n"
            + "  <xs:complexType name='b'><xs:sequence><xs:element name='w' type='t:missing2'/>"
            + "</xs:sequence></xs:complexType>\n");
        List<Recorded> messages = run(xsd);

        List<Recorded> resolveErrors = new ArrayList<>();
        for (Recorded m : messages) {
            Assert.assertEquals(xsd.getAbsolutePath(), m.file.getAbsolutePath());
            Assert.assertEquals(BuildContext.SEVERITY_ERROR, m.severity);
            if (m.message.contains("src-resolve")) {
                resolveErrors.add(m);
            }
        }
        Assert.assertEquals(2, resolveErrors.size());
        Assert.assertTrue(resolveErrors.get(0).message, resolveErrors.get(0).message.contains("t:missing1"));
        Assert.assertEquals(2, resolveErrors.get(0).line);
        Assert.assertTrue(resolveErrors.get(1).message, resolveErrors.get(1).message.contains("t:missing2"));
        Assert.assertEquals(3, resolveErrors.get(1).line);
        Assert.assertTrue(resolveErrors.get(0).column > 0);
    }

    @Test
    public void testWarning() throws Exception {
        // more values than XJC maps to an enum by default
        StringBuilder values = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            values.append("<xs:enumeration value='v").append(i).append("'/>");
        }
        File xsd = schema("warning.xsd",
            "  <xs:simpleType name='big'><xs:restriction base='xs:string'>" + values
            + "</xs:restriction></xs:simpleType>\n");
        List<Recorded> messages = run(xsd);

        Assert.assertEquals(1, messages.size());
        Assert.assertEquals(xsd.getAbsolutePath(), messages.get(0).file.getAbsolutePath());
        Assert.assertEquals(BuildContext.SEVERITY_WARNING, messages.get(0).severity);
        Assert.assertTrue(messages.get(0).message, messages.get(0).message.contains("big"));
    }

    @Test
    public void testSchemaTextCannotForgeMessages() throws Exception {
        // a default value is quoted, line breaks included, in the error for its invalid length
        String forged = "&#10;" + XJCBuildContext.MESSAGE_PREFIX.replace("\t", "&#9;")
            + "2&#9;7&#9;7&#9;/etc/forged.xsd&#9;forged&#10;MSG: /etc/forged.xsd&#10;Col: junk&#10;DONE";
        File xsd = schema("forged.xsd",
            "  <xs:element name='e' default='ab" + forged + "'><xs:simpleType>"
            + "<xs:restriction base='xs:string'><xs:maxLength value='1'/></xs:restriction>"
            + "</xs:simpleType></xs:element>\n");
        List<Recorded> messages = run(xsd);

        Assert.assertFalse(messages.isEmpty());
        boolean quoted = false;
        for (Recorded m : messages) {
            Assert.assertEquals(xsd.getAbsolutePath(), m.file.getAbsolutePath());
            quoted |= m.message.contains("MSG: /etc/forged.xsd\nCol: junk");
        }
        Assert.assertTrue("schema text should only appear inside the genuine message", quoted);
    }

    private File schema(String name, String content) throws Exception {
        File xsd = new File(tmp.getRoot(), name);
        String schema = "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' xmlns:t='urn:t'"
            + " targetNamespace='urn:t'>\n" + content + "</xs:schema>\n";
        Files.write(xsd.toPath(), schema.getBytes(StandardCharsets.UTF_8));
        return xsd;
    }

    // run XJC with the forked process's build context, then parse its output as the mojo does
    private List<Recorded> run(File xsd) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintStream origOut = System.out;
        PrintStream origErr = System.err;
        try {
            System.setOut(new PrintStream(out, true, "UTF-8"));
            System.setErr(new PrintStream(err, true, "UTF-8"));
            XJCErrorListener listener = new XJCErrorListener(new XJCBuildContext());
            new XSDToJavaRunner(new String[] {"-d", tmp.newFolder().getAbsolutePath(), xsd.toURI().toString()},
                                listener, xsd, Collections.<String>emptyList()).run();
        } finally {
            System.setOut(origOut);
            System.setErr(origErr);
        }

        RecordingBuildContext context = new RecordingBuildContext();
        ForkedOutputConsumer consumer = new ForkedOutputConsumer(context, new SystemStreamLog());
        for (ByteArrayOutputStream stream : new ByteArrayOutputStream[] {out, err}) {
            for (String line : new String(stream.toByteArray(), StandardCharsets.UTF_8).split("\r?\n")) {
                consumer.consumeLine(line);
            }
        }
        return context.messages;
    }
}
