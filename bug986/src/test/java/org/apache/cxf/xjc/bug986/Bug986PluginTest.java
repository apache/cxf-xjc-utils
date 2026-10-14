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

package org.apache.cxf.xjc.bug986;

import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import com.sun.tools.xjc.Driver;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class Bug986PluginTest {

    private static final String ANY_SIMPLE_TYPE = "@XmlSchemaType(name = \"anySimpleType\")";

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void testWithoutPlugin() throws Exception {
        String src = generate(false);
        Assert.assertTrue(annotationsOf(src, "protected Integer intAttr;").contains(ANY_SIMPLE_TYPE));
        Assert.assertTrue(annotationsOf(src, "protected String stringAttr;").contains(ANY_SIMPLE_TYPE));
    }

    @Test
    public void testRemovesAnySimpleTypeFromNonStringFields() throws Exception {
        String src = generate(true);
        Assert.assertFalse(annotationsOf(src, "protected Integer intAttr;").contains(ANY_SIMPLE_TYPE));
        Assert.assertTrue(annotationsOf(src, "protected String stringAttr;").contains(ANY_SIMPLE_TYPE));
    }

    /*
     * Run XJC on the module path, where the codemodel module does not open its
     * packages to the plugin, so reflection on codemodel internals fails.
     */
    @Test
    public void testOnModulePath() throws Exception {
        List<String> modulePath = new ArrayList<>();
        for (String e : System.getProperty("java.class.path").split(File.pathSeparator)) {
            if (e.endsWith(".jar")) {
                modulePath.add(e);
            }
        }
        // the plugin's classes as an automatic module
        modulePath.add(jar(Paths.get(Bug986Plugin.class.getProtectionDomain().getCodeSource().getLocation().toURI()),
                           tmp.newFile("cxf-xjc-bug986.jar").toPath()));
        File out = tmp.newFolder();
        String schema = new File(getClass().getResource("/bug986.xsd").toURI()).getAbsolutePath();
        Process p = new ProcessBuilder(
            Paths.get(System.getProperty("java.home"), "bin", "java").toString(),
            "--module-path", String.join(File.pathSeparator, modulePath),
            "--add-modules", "ALL-MODULE-PATH",
            "com.sun.tools.xjc.XJCFacade",
            "-extension", "-Xbug986", "-d", out.getAbsolutePath(), schema)
            .inheritIO().start();
        Assert.assertTrue("XJC did not finish", p.waitFor(2, TimeUnit.MINUTES));
        Assert.assertEquals(0, p.exitValue());

        File holder = new File(out, "org/apache/cxf/bug986/Holder.java");
        String src = new String(Files.readAllBytes(holder.toPath()), StandardCharsets.UTF_8);
        Assert.assertFalse(annotationsOf(src, "protected Integer intAttr;").contains(ANY_SIMPLE_TYPE));
        Assert.assertTrue(annotationsOf(src, "protected String stringAttr;").contains(ANY_SIMPLE_TYPE));
    }

    private static String jar(Path classes, Path jar) throws Exception {
        try (OutputStream os = Files.newOutputStream(jar);
            JarOutputStream jos = new JarOutputStream(os);
            Stream<Path> files = Files.walk(classes)) {
            for (Path f : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                jos.putNextEntry(new JarEntry(classes.relativize(f).toString().replace(File.separatorChar, '/')));
                Files.copy(f, jos);
                jos.closeEntry();
            }
        }
        return jar.toString();
    }

    private String generate(boolean withPlugin) throws Exception {
        File out = tmp.newFolder();
        String schema = new File(getClass().getResource("/bug986.xsd").toURI()).getAbsolutePath();
        String[] args = withPlugin
            ? new String[] {"-extension", "-Xbug986", "-d", out.getAbsolutePath(), schema}
            : new String[] {"-extension", "-d", out.getAbsolutePath(), schema};
        Assert.assertEquals(0, Driver.run(args, System.out, System.out));
        File holder = new File(out, "org/apache/cxf/bug986/Holder.java");
        return new String(Files.readAllBytes(holder.toPath()), StandardCharsets.UTF_8);
    }

    // the annotations written between the previous declaration and the given field
    private static String annotationsOf(String src, String field) {
        int idx = src.indexOf(field);
        Assert.assertTrue(field + " not generated", idx != -1);
        int start = Math.max(src.lastIndexOf(';', idx), src.lastIndexOf('{', idx));
        return src.substring(start + 1, idx);
    }
}
