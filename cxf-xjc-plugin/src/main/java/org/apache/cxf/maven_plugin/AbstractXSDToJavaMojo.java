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

import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

import com.sun.codemodel.CodeWriter;
import com.sun.codemodel.JCodeModel;
import com.sun.tools.xjc.ErrorReceiver;
import com.sun.tools.xjc.Language;
import com.sun.tools.xjc.ModelLoader;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.model.Model;
import com.sun.tools.xjc.outline.Outline;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;
import org.apache.xml.resolver.CatalogManager;
import org.apache.xml.resolver.tools.CatalogResolver;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * @description CXF XSD To Java Tool
 */
public abstract class AbstractXSDToJavaMojo extends AbstractMojo {   
    @Component
    protected MavenProject project;
    
    @Parameter
    XsdOption xsdOptions[];
    
    /**
     * Directory in which the "DONE" markers are saved that 
     */
    @Parameter(defaultValue = "${project.build.directory}/cxf-xsd-plugin-markers",
        property = "cxf.markerDirectory")
    File markerDirectory;
    
    /**
     * The extension artifacts that will be retrieved and added to the classpath.
     */
    @Parameter
    private List<String> extensions;
    
       
    @Component
    private BuildContext buildContext;
                
    @Component
    private RepositorySystem repository;
        
    @Component
    private MavenSession session;
    
    
    abstract String getOutputDir();
    
    
    
    class XJCErrorListener extends ErrorReceiver {
        private final List<File> errorfiles;
        private Exception firstError;
        
        XJCErrorListener(List<File> errorfiles) {
            this.errorfiles = errorfiles;
        }
        public Exception getFirstError() {
            return firstError;
        }

        public void error(Exception exception) {
            if (firstError == null) {
                firstError = exception;
                firstError.fillInStackTrace();
            }
        }
        public void error(SAXParseException exception) {
            final String sysId = exception.getSystemId();
            File file = mapFile(sysId);
            if (file != null && !errorfiles.contains(file)) {
                buildContext.removeMessages(file);
                errorfiles.add(file);
            }
            
            buildContext.addMessage(file, exception.getLineNumber(), exception.getColumnNumber(),
                                    mapMessage(exception.getLocalizedMessage()),
                                    BuildContext.SEVERITY_ERROR, exception);
            if (firstError == null) {
                firstError = exception;
                firstError.fillInStackTrace();
            }
        }

        private String mapMessage(String localizedMessage) {
            return localizedMessage;
        }

        private File mapFile(String s) {
            File file = null;
            if (s != null && s.startsWith("file:")) {
                if (s.contains("#")) {
                    s = s.substring(0, s.indexOf('#'));
                }
                try {
                    URI uri = new URI(s);
                    file = new File(uri);
                } catch (URISyntaxException e) {
                    //ignore
                }
            }
            if (file == null) {
                //Cannot pass a null into buildContext.addMessage.  Create a pointless
                //File object that maps to the systemId
                if (s == null) {
                    file = new File("null");
                } else {
                    final String s2 = s;
                    file = new File(s2) {
                        private static final long serialVersionUID = 1L;
                        public String getAbsolutePath() {
                            return s2;
                        }
                    };
                }
            }                     
            return file;
        }

        public void fatalError(SAXParseException exception) {
            error(exception);
            if (firstError == null) {
                firstError = exception;
                firstError.fillInStackTrace();
            }
        }

        public void warning(SAXParseException exception) {
            File file = mapFile(exception.getSystemId());
            if (file != null && !errorfiles.contains(file)) {
                buildContext.removeMessages(file);
                errorfiles.add(file);
            }
            buildContext.addMessage(file, exception.getLineNumber(), exception.getColumnNumber(),
                                    mapMessage(exception.getLocalizedMessage()),
                                    BuildContext.SEVERITY_WARNING, exception);
        }

        public void info(SAXParseException exception) {
            //System.out.println(mapFile(exception.getSystemId()));
        }
        public void message(File file, String string) {
            buildContext.addMessage(file, 0, 0,
                                    mapMessage(string),
                                    BuildContext.SEVERITY_ERROR, null);
        }
        public void warning(File file, Exception e) {
            buildContext.addMessage(file, 0, 0,
                                    mapMessage(e.getLocalizedMessage()),
                                    BuildContext.SEVERITY_WARNING, e);
        }
    }
    
    private URI mapLocation(String s) throws MojoExecutionException {
        try {
            File file = new File(s);
            URI uri;
            if (file.exists()) {
                uri = file.toURI();
            } else {
                file = new File(project.getBasedir(), s);
                if (file.exists()) {
                    uri = file.toURI();
                } else {
                    uri = new URI(s);
                }
            }
            if ("classpath".equals(uri.getScheme())) {
                URL url = Thread.currentThread().getContextClassLoader()
                    .getResource(s.substring(10));
                if (url == null) {
                    url = Thread.currentThread().getContextClassLoader()
                        .getResource(s.substring(11));
                }
                if (url != null) {
                    uri = url.toURI();
                }
            }
            return uri;
        } catch (URISyntaxException e1) {
            throw new MojoExecutionException("Could not map " + s, e1);
        }
    }
    public void execute() throws MojoExecutionException {
        String outputDir = getOutputDir();
        
        File outputDirFile = new File(outputDir);
        outputDirFile.mkdirs();
        markerDirectory.mkdirs();

        boolean result = true;
        
        if (xsdOptions == null) {
            throw new MojoExecutionException("Must specify xsdOptions");           
        }
        List<File> errorFiles = new ArrayList<File>();
    
        for (int x = 0; x < xsdOptions.length; x++) {
            ClassLoader origLoader = Thread.currentThread().getContextClassLoader();
            try {
                String[] args = getArguments(xsdOptions[x], outputDir);
                URI xsdURI = mapLocation(xsdOptions[x].getXsd());
                URI basedir = project.getBasedir().toURI();
                
                String doneFileName = xsdURI.toString();
                if (doneFileName.startsWith(basedir.toString())) {
                    doneFileName = doneFileName.substring(basedir.toString().length());
                }
                
                doneFileName = doneFileName.replace('?', '_')
                    .replace('&', '_').replace('/', '_').replace('\\', '_')
                    .replace(':', '_').replace('!', '_');
                
                // If URL to WSDL, replace ? and & since they're invalid chars for file names
                File doneFile =
                    new File(markerDirectory, "." + doneFileName + ".DONE");
                
                long srctimestamp = 0;
                if ("file".equals(xsdURI.getScheme())) {
                    srctimestamp = new File(xsdURI).lastModified();
                } else {
                    try {
                        srctimestamp = xsdURI.toURL().openConnection().getDate();
                    } catch (Exception e) {
                        //ignore
                    }
                }
                if (xsdOptions[x].getBindingFile() != null) { 
                    URI bindingURI = mapLocation(xsdOptions[x].getBindingFile());
                    if ("file".equals(bindingURI.getScheme())) {
                        long bts = new File(bindingURI).lastModified();
                        if (bts > srctimestamp) {
                            srctimestamp = bts;
                        }
                    }
                }

                boolean doWork = false;
                if (!doneFile.exists()) {
                    doWork = true;
                } else if (srctimestamp > doneFile.lastModified()) {
                    doWork = true;
                } else {
                    File files[] = xsdOptions[x].getDependencies();
                    if (files != null) {
                        for (int z = 0; z < files.length; ++z) {
                            if (files[z].lastModified() > doneFile.lastModified()) {
                                doWork = true;
                            }
                        }
                    }
                }
                
                if (doWork) {
                    try {
                        File files[] = xsdOptions[x].getDependencies();
                        if (files != null) {
                            for (int z = 0; z < files.length; ++z) {
                                if (files[z].lastModified() > doneFile.lastModified()) {
                                    buildContext.removeMessages(files[z]);
                                }
                            }
                        }
                        removeMessages(xsdOptions[x].getXsd());
                        removeMessages(xsdOptions[x].getBindingFile());
                        
                        XJCErrorListener listener = new XJCErrorListener(errorFiles);
                        int i = run(args, listener, new File(xsdOptions[x].getXsd()));
                        if (i == 0) {
                            doneFile.delete();
                            doneFile.createNewFile();
                        } else if (listener.getFirstError() != null) {
                            throw listener.getFirstError();
                        }
                        File dirs[] = xsdOptions[x].getDeleteDirs();
                        if (dirs != null) {
                            for (int idx = 0; idx < dirs.length; ++idx) {
                                result = result && deleteDir(dirs[idx]);
                            }
                        }
                        buildContext.refresh(outputDirFile);
                    } catch (Exception e) {
                        throw new MojoExecutionException(e.getMessage(), e);
                    }
                }
            
                if (!result) {
                    throw new MojoExecutionException("Could not delete redundant dirs");
                }  
            } finally {
                Thread.currentThread().setContextClassLoader(origLoader);
            }
        }
    }
    
    private List<File> resolve(String artifactDescriptor) {
        String[] s = artifactDescriptor.split(":");

        String type = s.length >= 4 ? s[3] : "jar";
        Artifact artifact = repository.createArtifact(s[0], s[1], s[2], type);

        ArtifactResolutionRequest request = new ArtifactResolutionRequest();
        request.setArtifact(artifact);
        
        request.setResolveRoot(true).setResolveTransitively(true);
        request.setServers(session.getRequest().getServers());
        request.setMirrors(session.getRequest().getMirrors());
        request.setProxies(session.getRequest().getProxies());
        request.setLocalRepository(session.getLocalRepository());
        request.setRemoteRepositories(session.getRequest().getRemoteRepositories());
        ArtifactResolutionResult result = repository.resolve(request);
        List<File> files = new ArrayList<File>();
        for (Artifact a : result.getArtifacts()) {
            files.add(a.getFile());
        }
        if (!files.contains(artifact.getFile())) {
            files.add(artifact.getFile());
        }
        return files;
    }
    
    protected List<String> getClasspathElements() throws DependencyResolutionRequiredException {
        return project.getCompileClasspathElements();
    }
    
    private int run(String[] args, final XJCErrorListener listener, final File file) 
        throws Exception {
        
        List<String> cpList = getClasspathElements();
        List<URL> urls = new ArrayList<URL>();
        for (String s : cpList) {
            urls.add(new File(s).toURI().toURL());
        }
        final ClassLoader loader = new URLClassLoader(urls.toArray(new URL[urls.size()]));
        
        CatalogManager.getStaticManager().setIgnoreMissingProperties(true);
        final CatalogResolver catResolver = new CatalogResolver(true) {
            public InputSource resolveEntity(String publicId, String systemId) {
                String resolved = getResolvedEntity(publicId, systemId);
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
                    listener.warning(file, e);
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
        opt.setSchemaLanguage(Language.XMLSCHEMA);
        opt.parseArguments(args);
        Model model = ModelLoader.load(opt, new JCodeModel(), listener);
        if (model == null) {
            listener.message(file, "Failed to create model");
            return -1;
        }
        Outline outline = model.generateCode(opt, listener);
        if (outline == null) {
            listener.message(file, "Failed to generate code");
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
    private void removeMessages(String file) throws MojoExecutionException {
        if (file == null) {
            return;
        }
        URI location = mapLocation(file);
        if ("file".equals(location.getScheme())) {
            File f = new File(location);
            if (f.exists()) {
                buildContext.removeMessages(f);
            }
        }        
    }
    private String[] getArguments(XsdOption option, String outputDir) throws MojoExecutionException {
        List<URL> newCp = new ArrayList<URL>();
        List<String> list = new ArrayList<String>();
        if (extensions != null && extensions.size() > 0) {
            try {
                for (String ext : extensions) {
                    for (File file : resolve(ext)) {
                        list.add("-classpath");
                        list.add(file.getAbsolutePath());
                        newCp.add(file.toURI().toURL());
                    }
                }
            } catch (Exception ex) {
                throw new MojoExecutionException("Could not download extension artifact", ex);
            }
        }
        if (!newCp.isEmpty()) {
            Thread.currentThread()
                .setContextClassLoader(new URLClassLoader(newCp.toArray(new URL[newCp.size()]),
                                                          Thread.currentThread().getContextClassLoader()));
        }
        if (option.getPackagename() != null) {
            list.add("-p");
            list.add(option.getPackagename());
        }
        if (option.getBindingFile() != null) {
            list.add("-b");
            list.add(mapLocation(option.getBindingFile()).toString());
        }
        if (option.getCatalog() != null) {
            list.add("-catalog");
            list.add(option.getCatalog());
        }
        if (option.isExtension()) {
            list.add("-extension");
        }
        if (option.getExtensionArgs() != null) {
            list.addAll(option.getExtensionArgs());
        }          
        if (getLog().isDebugEnabled()) {
            list.add("-verbose");            
        }
        list.add("-d");
        list.add(outputDir);
        list.add(mapLocation(option.getXsd()).toString());
       
        return list.toArray(new String[list.size()]);
        
    }
    
    private boolean deleteDir(File f) {
        if (f.isDirectory()) {
            File files[] = f.listFiles();
            for (int idx = 0; idx < files.length; ++idx) {
                deleteDir(files[idx]);
            }
        }
        
        if (f.exists()) {
            return f.delete();
        }
        
        return true;
    }
}
