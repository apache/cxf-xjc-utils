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
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElementRef;

import org.xml.sax.InputSource;

import com.sun.codemodel.CodeWriter;
import com.sun.codemodel.JCodeModel;
import com.sun.tools.xjc.Language;
import com.sun.tools.xjc.ModelLoader;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.api.SpecVersion;
import com.sun.tools.xjc.model.Model;
import com.sun.tools.xjc.outline.Outline;

import org.apache.xml.resolver.CatalogManager;
import org.apache.xml.resolver.tools.CatalogResolver;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * 
 */
public class XSDToJavaRunner {
    final String[] args;
    final List<String> cpList;
    final XJCErrorListener listener;
    final File xsdFile;
    
    public XSDToJavaRunner(String[] args, XJCErrorListener listener,
                           File file, List<String> cp) {
        this.args = args;
        this.listener = listener;
        this.xsdFile = file;
        this.cpList = cp;
    }
    public int run() throws Exception {
        List<URL> urls = new ArrayList<URL>();
        for (String s : cpList) {
            urls.add(new File(s).toURI().toURL());
        }
        final ClassLoader loader = new URLClassLoader(urls.toArray(new URL[urls.size()]), 
                                                      this.getClass().getClassLoader());
        
        CatalogManager cm = new CatalogManager();
        cm.setUseStaticCatalog(false);
        cm.setIgnoreMissingProperties(true);
        final CatalogResolver catResolver = new CatalogResolver(cm) {
            public InputSource resolveEntity(String publicId, String systemId) {
                String resolved = getResolvedEntity(publicId, systemId);
                //System.out.println("Resolved: ");
                //System.out.println("        : " + publicId);
                //System.out.println("        : " + systemId);
                //System.out.println("        -> " + resolved);
                if (resolved == null) {
                    return null;
                }
                URL url;
                InputSource iSource = new InputSource(resolved);
                iSource.setPublicId(publicId);
                try {
                    if (resolved.startsWith("classpath:")) {
                        resolved = resolved.substring("classpath:".length());
                        url = loader.getResource(resolved);
                        iSource.setSystemId(url.toExternalForm());
                    } else {
                        url = new URL(resolved);
                    }
                    InputStream iStream = url.openStream();
                    iSource.setByteStream(iStream);

                    return iSource;
                } catch (Exception e) {
                    listener.warning(xsdFile, e);
                    return null;
                }
            }
        };
        final Options opt = new Options() {
            @Override
            public void addCatalog(File catalogFile) throws IOException {
                if (entityResolver == null) {
                    entityResolver = catResolver;
                }
                catResolver.getCatalog().parseCatalog(catalogFile.getPath());
            }
        };
        if (checkXmlElementRef()) {
            opt.target = SpecVersion.V2_1;
        }
        opt.setSchemaLanguage(Language.XMLSCHEMA);
        opt.parseArguments(args);
        Model model = ModelLoader.load(opt, new JCodeModel(), listener);
        if (model == null) {
            listener.message(xsdFile, "Failed to create model");
            return -1;
        }
        Outline outline = model.generateCode(opt, listener);
        if (outline == null) {
            listener.message(xsdFile, "Failed to generate code");
            return -1;
        }

        // then print them out
        try {
            CodeWriter cw = opt.createCodeWriter();
            model.codeModel.build(cw);
        } catch (IOException e) {
            listener.error(e);
            return -1;
        }
        return 0;        
    }
    private boolean checkXmlElementRef() {
        try {
            //check the version of JAXB-API that is actually being picked up
            //so we can set target=2.1 if the 2.1 version of XmlElementRef is picked up
            XmlElementRef.class.getMethod("required");
        } catch (Throwable t) {
            return true;
        }
        return false;
    }
    public static void main(String[] args) throws Exception {
        BuildContext context = new XJCBuildContext();
        XJCErrorListener listener = new XJCErrorListener(context);
        
        File outputFile = new File(args[args.length - 1]);
        int i = new XSDToJavaRunner(args, listener, outputFile, new ArrayList<String>()).run();
        System.exit(i);
    }

}
