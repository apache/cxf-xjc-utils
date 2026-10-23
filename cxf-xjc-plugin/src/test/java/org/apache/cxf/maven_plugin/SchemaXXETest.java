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
import java.util.Collections;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Verifies that a schema compiled by {@link XSDToJavaRunner} cannot read local files (or reach the
 * network) through a DOCTYPE or an external entity declared in the schema document (an XXE attack).
 */
public class SchemaXXETest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private int compile(String xsdText) throws Exception {
        File xsd = tmp.newFile("schema-" + System.nanoTime() + ".xsd");
        Files.write(xsd.toPath(), xsdText.getBytes(StandardCharsets.UTF_8));
        File out = tmp.newFolder("gen-" + System.nanoTime());
        XJCErrorListener listener = new XJCErrorListener(new XJCBuildContext());
        // "-d" directs generated code into the temporary folder so the test does not write into
        // the working directory (the module source tree).
        return new XSDToJavaRunner(
            new String[] {"-d", out.getAbsolutePath(), xsd.toURI().toString()},
            listener, out, Collections.<String>emptyList()).run();
    }

    // A schema that references an external general entity pointing at a real local file. If the
    // parser dereferenced it, the file would be read during compilation and the compile would
    // succeed; with external entities disabled the compile must fail instead.
    private String schemaReadingFile(File target) {
        return "<?xml version='1.0'?>\n"
            + "<!DOCTYPE xs:schema [ <!ENTITY xxe SYSTEM '" + target.toURI() + "'> ]>\n"
            + "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'"
            + " targetNamespace='urn:t' xmlns:t='urn:t'>\n"
            + "  <xs:element name='root' type='xs:string'>\n"
            + "    <xs:annotation><xs:documentation>&xxe;</xs:documentation></xs:annotation>\n"
            + "  </xs:element>\n"
            + "</xs:schema>\n";
    }

    @Test
    public void externalEntityInSchemaIsNotResolved() throws Exception {
        File secret = tmp.newFile("secret.txt");
        Files.write(secret.toPath(), "SECRET".getBytes(StandardCharsets.UTF_8));

        // Must NOT compile cleanly: resolving the external entity (reading the file) is refused.
        int rc = compile(schemaReadingFile(secret));
        Assert.assertEquals("external entity in a schema must not be resolved during compile", -1, rc);
    }

    @Test
    public void legitimateSchemaStillCompiles() throws Exception {
        // A normal schema (no DOCTYPE, no entities) is unaffected by the hardening.
        int rc = compile(
            "<?xml version='1.0'?>\n"
            + "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'"
            + " targetNamespace='urn:t' xmlns:t='urn:t'>\n"
            + "  <xs:element name='root' type='xs:string'/>\n"
            + "</xs:schema>\n");
        Assert.assertEquals("a schema with no external references must still compile", 0, rc);
    }

    @Test
    public void accessExternalDtdPropertyIsRestoredAfterRun() throws Exception {
        String sentinel = "file,jar:file";
        String prev = System.getProperty("javax.xml.accessExternalDTD");
        System.setProperty("javax.xml.accessExternalDTD", sentinel);
        try {
            compile(
                "<?xml version='1.0'?>\n"
                + "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'"
                + " targetNamespace='urn:t' xmlns:t='urn:t'>\n"
                + "  <xs:element name='root' type='xs:string'/>\n"
                + "</xs:schema>\n");
            Assert.assertEquals("the caller's accessExternalDTD setting must be restored",
                sentinel, System.getProperty("javax.xml.accessExternalDTD"));
        } finally {
            if (prev == null) {
                System.clearProperty("javax.xml.accessExternalDTD");
            } else {
                System.setProperty("javax.xml.accessExternalDTD", prev);
            }
        }
    }
}
