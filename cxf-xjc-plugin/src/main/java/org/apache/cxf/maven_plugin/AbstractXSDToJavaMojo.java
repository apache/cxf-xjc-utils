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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.archiver.jar.Manifest;
import org.codehaus.plexus.archiver.jar.Manifest.Attribute;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;
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
    
    /**
     * Allows running in a separate process.
     */
    @Parameter(defaultValue = "false")
    private boolean fork;
    
    /**
     * Sets the Java executable to use when fork parameter is <code>true</code>.
     */
    @Parameter(defaultValue = "${java.home}/bin/java")
    private String javaExecutable;
    
    
    /**
     * Sets the JVM arguments (i.e. <code>-Xms128m -Xmx128m</code>) if fork is set to <code>true</code>.
     */
    @Parameter(property = "cxf.xjc.jvmArgs")
    private String additionalJvmArgs;
    
    /**
     * The plugin dependencies, needed for the fork mode.
     */
    @Parameter(property = "plugin.artifacts", readonly = true, required = true)
    private List<Artifact> pluginArtifacts;    
    
    abstract String getOutputDir();
    
    
    
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
    
        for (int x = 0; x < xsdOptions.length; x++) {
            ClassLoader origLoader = Thread.currentThread().getContextClassLoader();
            try {
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
                        int i = run(xsdOptions[x], outputDir);
                        if (i == 0) {
                            doneFile.delete();
                            doneFile.createNewFile();
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
    
    private int run(XsdOption option, String outputDir) throws Exception {
        if (!fork) {
            String[] args = getArguments(option, outputDir);
            this.getLog().debug("Args: " + Arrays.asList(args));
            XJCErrorListener listener = new XJCErrorListener(buildContext);
            int i = new XSDToJavaRunner(args, listener, new File(option.getXsd()), getClasspathElements()).run();
            if (i != 0 && listener.getFirstError() != null) {
                throw listener.getFirstError();
            }
            return i;
        }
        return runForked(option, outputDir);
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
    private File getJavaExecutable() throws IOException {
        String exe = isWindows() && !javaExecutable.endsWith(".exe") ? ".exe" : "";
        File javaExe = new File(javaExecutable + exe);

        if (!javaExe.isFile()) {
            throw new IOException(
                                  "The java executable '"
                                      + javaExe
                                      + "' doesn't exist or is not a file." 
                                      + "Verify the <javaExecutable/> parameter.");
        }
        return javaExe;
    }
    private boolean isWindows() {
        String osName = System.getProperty("os.name");
        if (osName == null) {
            return false;
        }
        return osName.startsWith("Windows");
    }
    
    private int runForked(XsdOption option, String outputDir) throws Exception {
        String[] args = getArguments(option, outputDir);
        Commandline cmd = new Commandline();
        cmd.getShell().setQuotedArgumentsEnabled(true); // for JVM args
        cmd.setWorkingDirectory(project.getBuild().getDirectory());
        try {
            cmd.setExecutable(getJavaExecutable().getAbsolutePath());
        } catch (IOException e) {
            getLog().debug(e);
            throw new MojoExecutionException(e.getMessage(), e);
        }
        cmd.createArg().setLine(additionalJvmArgs);
        
        
        File file = null;
        try {
            // file = new File("/tmp/test.jar");
            file = File.createTempFile("cxf-xjc-plugin", ".jar");
            file.deleteOnExit();
            
            JarArchiver jar = new JarArchiver();
            jar.setDestFile(file.getAbsoluteFile());

            Manifest manifest = new Manifest();
            Attribute attr = new Attribute();
            attr.setName("Class-Path");
            StringBuilder b = new StringBuilder(8000);
            for (String cp : getClasspathElements()) {
                b.append(cp).append(' ');
            }
            for (Artifact a : pluginArtifacts) {
                b.append(a.getFile().getAbsolutePath()).append(' ');
            }
            attr.setValue(b.toString());
            manifest.getMainSection().addConfiguredAttribute(attr);

            attr = new Attribute();
            attr.setName("Main-Class");
            attr.setValue(XSDToJavaRunner.class.getName());
            manifest.getMainSection().addConfiguredAttribute(attr);

            jar.addConfiguredManifest(manifest);
            jar.createArchive();

            cmd.createArg().setValue("-jar");
            
            String tmpFilePath = file.getAbsolutePath();
            if (tmpFilePath.contains(" ")) {
                //ensure the path is in double quotation marks if the path contain space
                tmpFilePath = "\"" + tmpFilePath + "\"";
            }
            cmd.createArg().setValue(tmpFilePath);

        } catch (Exception e1) {
            throw new MojoExecutionException("Could not create runtime jar", e1);
        }
        cmd.addArguments(args);

        StreamConsumer out = new StreamConsumer() {
            File file;
            int severity;
            int linenum;
            int column;
            StringBuilder message = new StringBuilder();
            
            public void consumeLine(String line) {
                if (line.startsWith("DONE")) {
                    buildContext.addMessage(file, linenum, column, message.toString(), severity, null);
                } else if (line.startsWith("MSG: ")
                    || line.startsWith("ERROR: ")
                    || line.startsWith("WARNING: ")) {
                    file = new File(line.substring(line.indexOf(' ')).trim());
                    String type = line.substring(0, line.indexOf(':'));
                    if (type.contains("ERROR")) {
                        severity = BuildContext.SEVERITY_ERROR;
                    } else if (type.contains("WARNING")) {
                        severity = BuildContext.SEVERITY_WARNING;
                    } else {
                        severity = 0;
                    }
                    linenum = 0;
                    column = 0;
                    message.setLength(0);
                } else if (line.startsWith("Col: ")) {
                    column = Integer.parseInt(line.substring(line.indexOf(' ')).trim());
                } else if (line.startsWith("Line: ")) {
                    linenum = Integer.parseInt(line.substring(line.indexOf(' ')).trim());
                } else if (line.startsWith("Severity: ")) {
                    severity = Integer.parseInt(line.substring(line.indexOf(' ')).trim());
                } else {
                    message.append(line).append('\n');
                }
            }
        };
        int exitCode;
        try {
            exitCode = CommandLineUtils.executeCommandLine(cmd, out, out);
        } catch (CommandLineException e) {
            getLog().debug(e);
            throw new MojoExecutionException(e.getMessage(), e);
        }
        

        String cmdLine = CommandLineUtils.toString(cmd.getCommandline());

        if (exitCode != 0) {
            StringBuffer msg = new StringBuffer("\nExit code: ");
            msg.append(exitCode);
            msg.append('\n');
            msg.append("Command line was: ").append(cmdLine).append('\n').append('\n');

            throw new MojoExecutionException(msg.toString());
        }

        file.delete();
        return 0;
    }
    
}
