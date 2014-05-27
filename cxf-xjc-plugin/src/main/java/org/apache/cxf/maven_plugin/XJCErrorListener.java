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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.xml.sax.SAXParseException;

import com.sun.tools.xjc.ErrorReceiver;

import org.sonatype.plexus.build.incremental.BuildContext;

public class XJCErrorListener extends ErrorReceiver {
    private BuildContext buildContext;
    private final List<File> errorfiles = new ArrayList<File>();
    private Exception firstError;
    
    public XJCErrorListener(BuildContext context) {
        this.buildContext = context;
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