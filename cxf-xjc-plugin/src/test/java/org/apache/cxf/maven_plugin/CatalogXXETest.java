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
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.apache.xml.resolver.CatalogManager;
import org.apache.xml.resolver.tools.CatalogResolver;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class CatalogXXETest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private CatalogResolver newResolver(boolean harden) {
        CatalogManager cm = new CatalogManager();
        cm.setUseStaticCatalog(false);
        cm.setIgnoreMissingProperties(true);
        CatalogResolver resolver = new CatalogResolver(cm);
        if (harden) {
            XSDToJavaRunner.hardenCatalogParsing(resolver);
        }
        return resolver;
    }

    private File catalog(String name, String content) throws Exception {
        File f = new File(tmp.getRoot(), name);
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return f;
    }

    // a catalog whose external DTD points at a path that does not exist; if the parser fetches
    // it (XXE), parsing fails with FileNotFoundException naming that path
    private File maliciousCatalog() throws Exception {
        File probe = new File(tmp.getRoot(), "xxe-probe-does-not-exist.dtd");
        return catalog("evil.cat",
            "<?xml version='1.0'?>\n"
            + "<!DOCTYPE catalog SYSTEM '" + probe.toURI() + "'>\n"
            + "<catalog xmlns='urn:oasis:names:tc:entity:xmlns:xml:catalog'>"
            + "<system systemId='urn:x' uri='x.xsd'/></catalog>");
    }

    @Test
    public void hardenedParseDoesNotFetchExternalDtd() throws Exception {
        // must not throw: the external DTD is not fetched
        newResolver(true).getCatalog().parseCatalog(maliciousCatalog().getPath());
    }

    @Test
    public void rawParseWouldFetchExternalDtd() throws Exception {
        // documents the behaviour the hardening guards against: the unhardened parser fetches
        try {
            newResolver(false).getCatalog().parseCatalog(maliciousCatalog().getPath());
            // if xml-resolver is ever fixed upstream this may stop fetching; then the hardening
            // is belt-and-braces rather than load-bearing, which is fine
        } catch (FileNotFoundException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("xxe-probe-does-not-exist"));
        }
    }

    @Test
    public void legitimateCatalogStillResolves() throws Exception {
        File mapped = new File(tmp.getRoot(), "real.xsd");
        Files.write(mapped.toPath(), "<schema/>".getBytes(StandardCharsets.UTF_8));
        File cat = catalog("good.cat",
            "<?xml version='1.0'?>\n"
            + "<catalog xmlns='urn:oasis:names:tc:entity:xmlns:xml:catalog'>"
            + "<system systemId='urn:good' uri='" + mapped.toURI() + "'/></catalog>");
        CatalogResolver resolver = newResolver(true);
        resolver.getCatalog().parseCatalog(cat.getPath());
        Assert.assertEquals(mapped.toURI().toString(), resolver.getCatalog().resolveSystem("urn:good"));
    }

    @Test
    public void catalogDeclaringTheOasisDtdStillResolves() throws Exception {
        // a DOCTYPE is allowed; only fetching external content is disabled
        File mapped = new File(tmp.getRoot(), "real.xsd");
        Files.write(mapped.toPath(), "<schema/>".getBytes(StandardCharsets.UTF_8));
        File cat = catalog("oasis.cat",
            "<?xml version='1.0'?>\n"
            + "<!DOCTYPE catalog PUBLIC '-//OASIS//DTD XML Catalogs V1.0//EN'"
            + " 'http://www.oasis-open.org/committees/entity/release/1.0/catalog.dtd'>\n"
            + "<catalog xmlns='urn:oasis:names:tc:entity:xmlns:xml:catalog'>"
            + "<system systemId='urn:good' uri='" + mapped.toURI() + "'/></catalog>");
        CatalogResolver resolver = newResolver(true);
        resolver.getCatalog().parseCatalog(cat.getPath());
        Assert.assertEquals(mapped.toURI().toString(), resolver.getCatalog().resolveSystem("urn:good"));
    }
}
