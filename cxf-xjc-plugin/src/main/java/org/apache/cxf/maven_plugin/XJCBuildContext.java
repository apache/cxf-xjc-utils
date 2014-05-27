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
import java.io.OutputStream;
import java.util.List;

import org.codehaus.plexus.util.Scanner;
import org.sonatype.plexus.build.incremental.BuildContext;

public class XJCBuildContext implements BuildContext {
    public boolean hasDelta(String relpath) {
        return false;
    }
    public boolean hasDelta(File file) {
        return false;
    }
    public boolean hasDelta(@SuppressWarnings("rawtypes") List relpaths) {
        return false;
    }
    public void refresh(File file) {
    }

    public OutputStream newFileOutputStream(File file) throws IOException {
        return null;
    }
    public Scanner newScanner(File basedir) {
        return null;
    }
    public Scanner newDeleteScanner(File basedir) {
        return null;
    }
    public Scanner newScanner(File basedir, boolean ignoreDelta) {
        return null;
    }
    public boolean isIncremental() {
        return false;
    }
    public void setValue(String key, Object value) {
    }
    public Object getValue(String key) {
        return null;
    }
    public void addWarning(File file, int line, int column, String message, Throwable cause) {
        System.out.println("WARNING: " + file.getAbsolutePath());
        System.out.println("Line: " + line);
        System.out.println("Col: " + column);
        System.out.println(message);
        if (cause != null) {
            cause.printStackTrace(System.out);
        }
        System.out.println();
        System.out.println("DONE");
    }

    @Override
    public void addError(File file, int line, int column, String message, Throwable cause) {
        System.err.println("ERROR: " + file.getAbsolutePath());
        System.err.println("Line: " + line);
        System.err.println("Col: " + column);
        System.err.println(message);
        if (cause != null) {
            cause.printStackTrace(System.err);
        }
        System.err.println();
        System.err.println("DONE");
    }
    public void addMessage(File file, int line, int column, String message, int severity, Throwable cause) {
        System.out.println("MSG: " + file.getAbsolutePath());
        System.out.println("Severity: " + severity);
        System.out.println("Line: " + line);
        System.out.println("Col: " + column);
        System.out.println(message);
        if (cause != null) {
            cause.printStackTrace(System.out);
        }
        System.out.println();
        System.out.println("DONE");
    }
    public void removeMessages(File file) {
    }
    public boolean isUptodate(File target, File source) {
        return false;
    }

}
