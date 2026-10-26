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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.sonatype.plexus.build.incremental.BuildContext;  

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class XSDToJavaRunnerTest {
    @Rule public TemporaryFolder outputFolder = new TemporaryFolder();
    
    @Test
    public void testCatalogAndBindings() throws Exception {
        final BuildContext context = new XJCBuildContext();
        final XJCErrorListener listener = new XJCErrorListener(context);

        final File outputFile = outputFolder.newFile();
        final File genDir = outputFolder.newFolder("gen");
        final String xjb = getClass().getResource("/schemas/wsdl/test.xjb").toExternalForm();
        final String xsd = getClass().getResource("/schemas/wsdl/test.xsd").toExternalForm();
        final String catalog = getClass().getResource("/schemas/configuration/catalog.cat").toExternalForm();

        new XSDToJavaRunner(
            new String [] {
                "-catalog", catalog,
                "-b", xjb,
                "-d", genDir.getAbsolutePath(),
                xsd
            },
            listener, 
            outputFile, 
            Collections.<String>emptyList()).run();
        
        assertThat(listener.getFirstError(), is(nullValue()));
    }

    /**
     * Records the messages reported, instead of printing them.
     */
    private static final class RecordingBuildContext extends XJCBuildContext {
        final List<String> messages = new ArrayList<>();

        @Override
        public void addMessage(File file, int line, int column, String message, int severity, Throwable cause) {
            messages.add(message);
        }
    }

    // compiles a schema importing namespace urn:imported, which the catalog maps to classpath:<resource>
    private int compileWithClasspathCatalog(String resource, List<String> classpath,
                                            RecordingBuildContext context) throws Exception {
        File dir = outputFolder.newFolder();
        File xsd = new File(dir, "main.xsd");
        Files.write(xsd.toPath(), ("<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'"
            + " xmlns:i='urn:imported' targetNamespace='urn:main'>"
            + "<xs:import namespace='urn:imported' schemaLocation='imported.xsd'/>"
            + "<xs:element name='root' type='i:T'/></xs:schema>").getBytes(StandardCharsets.UTF_8));
        File catalog = new File(dir, "catalog.xml");
        Files.write(catalog.toPath(), ("<catalog xmlns='urn:oasis:names:tc:entity:xmlns:xml:catalog'>"
            + "<public publicId='urn:imported' uri='classpath:" + resource + "'/>"
            + "</catalog>").getBytes(StandardCharsets.UTF_8));
        File genDir = outputFolder.newFolder();
        String[] args = {"-catalog", catalog.getAbsolutePath(), "-d", genDir.getAbsolutePath(), xsd.toURI().toString()};
        return new XSDToJavaRunner(args, new XJCErrorListener(context), genDir, new ArrayList<>(classpath)).run();
    }

    @Test
    public void testClasspathCatalogEntry() throws Exception {
        File cpDir = outputFolder.newFolder();
        Files.createDirectories(new File(cpDir, "imports").toPath());
        Files.write(new File(cpDir, "imports/imported.xsd").toPath(),
            ("<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' targetNamespace='urn:imported'>"
            + "<xs:simpleType name='T'><xs:restriction base='xs:string'/></xs:simpleType></xs:schema>")
            .getBytes(StandardCharsets.UTF_8));

        RecordingBuildContext context = new RecordingBuildContext();
        int rc = compileWithClasspathCatalog("imports/imported.xsd",
                                             Collections.singletonList(cpDir.getAbsolutePath()), context);
        assertEquals(context.messages.toString(), 0, rc);
    }

    @Test
    public void testClasspathCatalogEntryNotFound() throws Exception {
        RecordingBuildContext context = new RecordingBuildContext();
        // the import can then not be resolved, so the compile fails
        compileWithClasspathCatalog("does/not/exist.xsd", Collections.<String>emptyList(), context);

        String messages = context.messages.toString();
        assertTrue(messages, messages.contains("Resource does/not/exist.xsd, which the catalog maps"));
        assertTrue(messages, messages.contains("is not on the classpath"));
        assertFalse(messages, messages.contains("NullPointerException"));
    }
}
