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
import java.util.Collections;

import org.sonatype.plexus.build.incremental.BuildContext;  

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

public class XSDToJavaRunnerTest {
    @Rule public TemporaryFolder outputFolder = new TemporaryFolder();
    
    @Test
    public void testCatalogAndBindings() throws Exception {
        final BuildContext context = new XJCBuildContext();
        final XJCErrorListener listener = new XJCErrorListener(context);

        final File outputFile = outputFolder.newFile();
        final String xjb = getClass().getResource("/schemas/wsdl/test.xjb").toExternalForm();
        final String xsd = getClass().getResource("/schemas/wsdl/test.xsd").toExternalForm();
        final String catalog = getClass().getResource("/schemas/configuration/catalog.cat").toExternalForm();
        
        new XSDToJavaRunner(
            new String [] {
                "-catalog", catalog,
                "-b", xjb,
                xsd 
            }, 
            listener, 
            outputFile, 
            Collections.<String>emptyList()).run();
        
        assertThat(listener.getFirstError(), is(nullValue()));
    }
}
