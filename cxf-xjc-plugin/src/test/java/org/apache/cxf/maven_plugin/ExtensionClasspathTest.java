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
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.xml.sax.ErrorHandler;

import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.Plugin;
import com.sun.tools.xjc.outline.Outline;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * XJC instantiates every plugin registered in META-INF/services on the classpath it loads plugins
 * from, so that must only hold the configured extensions, not the project's dependencies.
 */
public class ExtensionClasspathTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    /**
     * A plugin that records whether XJC instantiated it.
     */
    public static class ProbePlugin extends Plugin {
        static volatile boolean instantiated;

        public ProbePlugin() {
            instantiated = true;
        }

        @Override
        public String getOptionName() {
            return "Xcxf-probe";
        }

        @Override
        public String getUsage() {
            return "";
        }

        @Override
        public boolean run(Outline outline, Options opt, ErrorHandler errorHandler) {
            return true;
        }
    }

    @Before
    public void reset() {
        ProbePlugin.instantiated = false;
    }

    // a jar registering ProbePlugin, as a dependency with a hostile META-INF/services entry would
    private File probeJar() throws Exception {
        File jar = tmp.newFile("probe-" + System.nanoTime() + ".jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar.toPath()))) {
            out.putNextEntry(new JarEntry("META-INF/services/" + Plugin.class.getName()));
            out.write(ProbePlugin.class.getName().getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return jar;
    }

    private int compile(List<String> extraArgs, List<String> projectClasspath) throws Exception {
        File xsd = tmp.newFile("schema-" + System.nanoTime() + ".xsd");
        Files.write(xsd.toPath(), ("<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'"
            + " targetNamespace='urn:t'><xs:element name='root' type='xs:string'/></xs:schema>")
            .getBytes(StandardCharsets.UTF_8));
        File out = tmp.newFolder("gen-" + System.nanoTime());
        List<String> args = new ArrayList<>(extraArgs);
        // -Xlocator is a plugin built into XJC: enabling it makes XJC look up all plugins
        args.addAll(Arrays.asList("-Xlocator", "-d", out.getAbsolutePath(), xsd.toURI().toString()));
        XJCErrorListener listener = new XJCErrorListener(new XJCBuildContext());
        return new XSDToJavaRunner(args.toArray(new String[0]), listener, out,
                                   new ArrayList<>(projectClasspath)).run();
    }

    @Test
    public void pluginOnProjectClasspathIsNotLoaded() throws Exception {
        File jar = probeJar();
        int rc = compile(Collections.<String>emptyList(), Collections.singletonList(jar.getAbsolutePath()));
        Assert.assertEquals(0, rc);
        Assert.assertFalse("a plugin registered by a project dependency must not be instantiated",
                           ProbePlugin.instantiated);
    }

    @Test
    public void pluginInExtensionIsLoaded() throws Exception {
        File jar = probeJar();
        int rc = compile(Arrays.asList("-classpath", jar.getAbsolutePath(), "-Xcxf-probe"),
                         Collections.<String>emptyList());
        Assert.assertEquals(0, rc);
        Assert.assertTrue("a plugin from a configured extension must be loaded", ProbePlugin.instantiated);
    }

    @Test
    public void projectClasspathIsReadFromManifest() throws Exception {
        File jar = tmp.newFile("forked.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(new Attributes.Name(XSDToJavaRunner.PROJECT_CLASSPATH_ATTRIBUTE),
                                         "file:/a/b.jar file:/c/d%20e/ ");
        try (OutputStream out = new JarOutputStream(Files.newOutputStream(jar.toPath()), manifest)) {
            // just the manifest
        }
        Assert.assertEquals(Arrays.asList("file:/a/b.jar", "file:/c/d%20e/"),
                            XSDToJavaRunner.getProjectClasspath(jar));
        Assert.assertEquals(Collections.emptyList(),
                            XSDToJavaRunner.getProjectClasspath(new File(tmp.getRoot(), "missing.jar")));
    }
}
