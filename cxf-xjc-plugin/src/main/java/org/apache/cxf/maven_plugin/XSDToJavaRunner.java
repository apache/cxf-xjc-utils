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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.XMLFilterImpl;

import com.sun.codemodel.CodeWriter;
import com.sun.codemodel.JCodeModel;
import com.sun.istack.SAXParseException2;
import com.sun.tools.xjc.ErrorReceiver;
import com.sun.tools.xjc.Language;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.api.SpecVersion;
import com.sun.tools.xjc.model.Model;
import com.sun.tools.xjc.outline.Outline;
import com.sun.tools.xjc.reader.internalizer.AbstractReferenceFinderImpl;
import com.sun.tools.xjc.reader.internalizer.DOMForest;
import com.sun.tools.xjc.reader.xmlschema.parser.XMLSchemaInternalizationLogic;

import jakarta.xml.bind.annotation.XmlElementRef;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import org.apache.xml.resolver.CatalogManager;
import org.apache.xml.resolver.readers.SAXCatalogReader;
import org.apache.xml.resolver.tools.CatalogResolver;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * 
 */
public class XSDToJavaRunner {
    // The manifest attribute of the forked process's jar holding the project classpath
    static final String PROJECT_CLASSPATH_ATTRIBUTE = "CXF-XJC-Project-Classpath";

    static Class<?> modelLoaderClass;

    // The JAXP system property (JAXP 1.5) that restricts which protocols may be used to resolve
    // external DTDs and external entities; an empty value forbids all such resolution. This is the
    // system-property name, which differs from the equivalent factory attribute in XMLConstants.
    private static final String ACCESS_EXTERNAL_DTD_PROPERTY = "javax.xml.accessExternalDTD";
    
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
    
    private static File getFile(String s, XJCErrorListener l) throws Exception {
        File f = new File(s);
        if (f.exists()) {
            return f;
        }
        try {
            URI uri = new URI(s);
            f = new File(uri);
            return f;
        } catch (Throwable t) {
            if (l != null) {
                l.debug("Could not find a file for " + s);
            }
            return null;
        }
    }
    
    /**
     * Replaces the SAX reader a catalog resolver uses to parse XML catalog files with one whose
     * parser does not resolve external entities or DTDs, so that a catalog cannot be used as an
     * XXE vector (reading local files or making network requests while it is parsed). A DOCTYPE
     * declaration is still allowed, so a legitimate OASIS catalog that declares the catalog DTD
     * keeps working; only the fetching of external content is disabled. This mirrors the reader
     * registration done by the resolver itself (see Catalog.setupReaders), with a hardened parser
     * factory, and leaves the plain-text catalog reader (which does no XML parsing) in place.
     */
    static void hardenCatalogParsing(CatalogResolver catalogResolver) {
        SAXCatalogReader reader = new SAXCatalogReader(secureSAXParserFactory());
        reader.setCatalogParser(null, "XCatalog",
                                "org.apache.xml.resolver.readers.XCatalogReader");
        reader.setCatalogParser("urn:oasis:names:tc:entity:xmlns:xml:catalog", "catalog",
                                "org.apache.xml.resolver.readers.OASISXMLCatalogReader");
        catalogResolver.getCatalog().addReader("application/xml", reader);
    }

    private static SAXParserFactory secureSAXParserFactory() {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        setFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        setFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        setFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
        setFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        return factory;
    }

    private static void setFeature(SAXParserFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (ParserConfigurationException | SAXException e) {
            // a parser that does not know the feature cannot be hardened with it; the remaining
            // features still apply
        }
    }

    public int run() throws Exception {
        List<URL> urls = new ArrayList<URL>();
        for (String s : cpList) {
            File file = getFile(s, listener);
            if (file != null) {
                urls.add(file.toURI().toURL());
            }
        }
        // The "-classpath" arguments are the configured extension artifacts. XJC plugins are only
        // loaded from these (and the cxf-xjc-plugin's own dependencies), not from the project's
        // classpath above: XJC instantiates every plugin registered in META-INF/services on the
        // classpath it is given, so any dependency of the project could otherwise run code here.
        List<URL> extensionUrls = new ArrayList<URL>();
        for (int x = 0; x < args.length - 1; x++) {
            if ("-classpath".equals(args[x])) {
                File file = getFile(args[x + 1], listener);
                if (file != null && file.exists()) {
                    extensionUrls.add(file.getAbsoluteFile().toURI().toURL());
                }
                x++;
            }
        }
        urls.addAll(extensionUrls);

        try (URLClassLoader loader = new URLClassLoader(urls.toArray(new URL[0]),
                                                      this.getClass().getClassLoader())) {
            final CatalogManager cm = new CatalogManager();
            cm.setUseStaticCatalog(false);
            cm.setIgnoreMissingProperties(true);
            final CatalogResolver catResolver = new CatalogResolver(cm) {
                public InputSource resolveEntity(String publicId, String systemId) {
                    final String resolved = getResolvedEntity(publicId, systemId);
                    if (resolved == null) {
                        return null;
                    }
                    InputSource iSource = new InputSource((systemId != null) ? systemId : resolved);
                    iSource.setPublicId(publicId);
                    try {
                        final URL url;
                        if (resolved.startsWith("classpath:")) {
                            url = loader.getResource(resolved.substring("classpath:".length()));
                            iSource.setSystemId(url.toExternalForm());
                        } else {
                            url = new URL(resolved);
                        }
                        InputStream iStream = url.openStream();
                        iSource.setByteStream(iStream);

                        //System.out.println("Resolved: " + publicId + " " + systemId + " " + url);
                        return iSource;
                    } catch (Exception e) {
                        listener.warning(xsdFile, e);
                        return null;
                    }
                }
            };
            hardenCatalogParsing(catResolver);
            final Options opt = new Options() {
                @Override
                public void addCatalog(File catalogFile) throws IOException {
                    if (entityResolver == null) {
                        entityResolver = catResolver;
                    }
                    catResolver.getCatalog().parseCatalog(catalogFile.getPath());
                }
            };

            // the project classpath stays available to the catalog resolver (for classpath: entries)
            // through "loader", but only the extensions are on the classpath XJC loads plugins from
            for (URL url : extensionUrls) {
                opt.classpaths.add(url);
            }
            if (checkXmlElementRef()) {
                opt.target = SpecVersion.V2_3;
            }
            opt.setSchemaLanguage(Language.XMLSCHEMA);
            // set up the context class loader so that the user-specified plugin
            // on classpath can be loaded from there with jaxb-xjc 2.3.0
            ClassLoader origLoader = Thread.currentThread().getContextClassLoader();
            // Parse the schema (and binding) documents with external DTD and external entity
            // resolution disabled, so that a crafted schema cannot read local files or reach the
            // network through a DOCTYPE or an external entity (an XXE attack). XML Schema has no
            // use for DTDs, so legitimate schemas are unaffected; schema import/include, which is
            // resolved through the entity resolver and catalog, is not touched by this property.
            final String prevAccessExternalDtd = System.getProperty(ACCESS_EXTERNAL_DTD_PROPERTY);
            System.setProperty(ACCESS_EXTERNAL_DTD_PROPERTY, "");
            final Model model;
            try {
                try {
                    Thread.currentThread().setContextClassLoader(opt.getUserClassLoader(origLoader));
                    opt.parseArguments(args);
                } finally {
                    Thread.currentThread().setContextClassLoader(origLoader);
                }
                model = loadModel(opt);
            } finally {
                if (prevAccessExternalDtd == null) {
                    System.clearProperty(ACCESS_EXTERNAL_DTD_PROPERTY);
                } else {
                    System.setProperty(ACCESS_EXTERNAL_DTD_PROPERTY, prevAccessExternalDtd);
                }
            }
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
        }
        return 0;
    }

    private synchronized Class<?> getModelLoaderClass() {
        if (modelLoaderClass == null) {
            try {
                ClassPool pool = ClassPool.getDefault();
                CtClass cc = pool.get("com.sun.tools.xjc.ModelLoader");
                cc.setName("com.sun.tools.xjc.ModelLoader");
                for (CtMethod m : cc.getMethods()) {
                    if ("buildDOMForest".equals(m.getName())) {
                        m.insertBefore("$1 = new " + CustomizedLogic.class.getName() + "();");
                    }
                }
                modelLoaderClass = cc.toClass();
            } catch (Throwable t) {
                try {
                    modelLoaderClass = Class.forName("com.sun.tools.xjc.ModelLoader");
                } catch (ClassNotFoundException e) {
                    //ignore
                }
            }
        }
        return modelLoaderClass;
    }
    
    private Model loadModel(Options opt) {
        try {
            return (Model)getModelLoaderClass()
                .getMethod("load", Options.class, JCodeModel.class, ErrorReceiver.class)
                .invoke(null, opt, new JCodeModel(), listener);
        } catch (Exception e) {
            listener.error("Failed to create model", e);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }
    
    public static class CustomizedLogic extends XMLSchemaInternalizationLogic {
        private static final class ReferenceFinder extends AbstractReferenceFinderImpl {
            private Locator locator;

            ReferenceFinder(DOMForest parent) {
                super(parent);
            }
            
            @Override
            public void setDocumentLocator(Locator l) {
                super.setDocumentLocator(l);
                locator = l;
            }
            
            @Override
            public void startElement(String namespaceURI, String localName, String qName, Attributes atts)
                throws SAXException {
                if (getContentHandler() != null) {
                    getContentHandler().startElement(namespaceURI, localName, qName, atts);
                }

                String relativeRef = findExternalResource(namespaceURI, localName, atts);
                if (relativeRef == null) {
                    return; // not found
                }
                try {
                    // absolutize URL.
                    String lsi = locator.getSystemId();
                    String ref;
                    URI relRefURI = new URI(relativeRef);
                    if (relRefURI.isAbsolute()) {
                        ref = relativeRef;
                    } else {
                        if (lsi.startsWith("jar:")) {
                            int bangIdx = lsi.indexOf('!');
                            if (bangIdx > 0) {
                                ref = lsi.substring(0, bangIdx + 1)
                                        + new URI(lsi.substring(bangIdx + 1)).resolve(new URI(relativeRef)).toString();
                            } else {
                                ref = relativeRef;
                            }
                        } else {
                            ref = new URI(lsi).resolve(new URI(relativeRef)).toString();
                        }
                    }

                    if (parent != null) {
                        ref = Options.normalizeSystemId(ref);

                        if (parent.get(ref) != null) {
                            return;
                        }
                        
                        InputSource is = null;
                        
                        // allow entity resolver to find the actual byte stream.
                        if (parent.getEntityResolver() != null) {
                            is = parent.getEntityResolver().resolveEntity(null, ref);
                        }
                        if (is == null) {
                            is = new InputSource(ref);
                        } else {
                            ref = is.getSystemId();
                        }
                        if (parent.get(ref) != null) {
                            is.getByteStream().close();
                            return;
                        }
                        parent.parse(ref, is, false);
                    }
                } catch (URISyntaxException e) {
                    String msg = e.getMessage();
                    if (new File(relativeRef).exists()) {
                        msg = "Filename is not a URI " + ' ' + msg;
                    }

                    SAXParseException spe = new SAXParseException2(
                            "Unable to parse " + relativeRef + ": " + msg,
                            locator, e);

                    fatalError(spe);
                    throw spe;
                } catch (IOException e) {
                    SAXParseException spe = new SAXParseException2(
                            "Unable to parse " + relativeRef + ": " + e.getMessage(),
                            locator, e);

                    fatalError(spe);
                    throw spe;
                }
            }
            
            protected String findExternalResource(String nsURI, String localName, Attributes atts) {
                if ("http://www.w3.org/2001/XMLSchema".equals(nsURI)
                    && ("import".equals(localName) || "include".equals(localName))) {
                    return atts.getValue("schemaLocation");
                }
                return null;
            }
        }

        public XMLFilterImpl createExternalReferenceFinder(DOMForest parent) {
            return new ReferenceFinder(parent);
        }
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

    /**
     * Reads the project classpath that AbstractXSDToJavaMojo records in the manifest of the jar
     * used to run a forked process. It is kept out of the manifest Class-Path, so that the
     * project's dependencies are not on the class path XJC loads plugins from.
     */
    static List<String> getProjectClasspath(File jar) throws IOException {
        List<String> cplist = new ArrayList<String>();
        if (!jar.isFile()) {
            return cplist;
        }
        try (JarFile jarFile = new JarFile(jar)) {
            Manifest manifest = jarFile.getManifest();
            String cp = manifest == null ? null : manifest.getMainAttributes().getValue(PROJECT_CLASSPATH_ATTRIBUTE);
            if (cp != null) {
                for (String s : cp.split(" ")) {
                    if (!s.isEmpty()) {
                        cplist.add(s);
                    }
                }
            }
        }
        return cplist;
    }

    public static void main(String[] args) throws Exception {
        BuildContext context = new XJCBuildContext();
        XJCErrorListener listener = new XJCErrorListener(context);

        // with "-jar" the class path is just the jar AbstractXSDToJavaMojo created for this run
        List<String> cplist = getProjectClasspath(new File(System.getProperty("java.class.path")));

        File outputFile = getFile(args[args.length - 1], listener);
        if (outputFile == null) {
            outputFile = new File(args[args.length - 1]);
        }
        int i = new XSDToJavaRunner(args, listener, outputFile, cplist).run();
        System.exit(i);
    }

}
