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
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import org.codehaus.plexus.util.Scanner;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * The BuildContext used when XJC runs in a forked process: messages are written to stdout/stderr
 * as single lines, which AbstractXSDToJavaMojo parses with {@link #parseMessage(String)}.
 * The file and message are escaped, so text from a schema (which can contain line breaks) can
 * neither spread over several lines nor be mistaken for a message of its own.
 */
public class XJCBuildContext implements BuildContext {
    static final String MESSAGE_PREFIX = "CXF-XJC-MESSAGE\t";

    /**
     * A message parsed from the output of the forked process.
     */
    static final class Message {
        final int severity;
        final int line;
        final int column;
        final File file;
        final String text;

        Message(int severity, int line, int column, File file, String text) {
            this.severity = severity;
            this.line = line;
            this.column = column;
            this.file = file;
            this.text = text;
        }
    }

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
        System.out.println(formatMessage(SEVERITY_WARNING, file, line, column, message, cause));
    }

    @Override
    public void addError(File file, int line, int column, String message, Throwable cause) {
        System.err.println(formatMessage(SEVERITY_ERROR, file, line, column, message, cause));
    }

    public void addMessage(File file, int line, int column, String message, int severity, Throwable cause) {
        PrintStream out = severity == SEVERITY_ERROR ? System.err : System.out;
        out.println(formatMessage(severity, file, line, column, message, cause));
    }

    static String formatMessage(int severity, File file, int line, int column, String message, Throwable cause) {
        StringBuilder text = new StringBuilder();
        if (message != null) {
            text.append(message);
        }
        if (cause != null) {
            StringWriter trace = new StringWriter();
            try (PrintWriter pw = new PrintWriter(trace)) {
                cause.printStackTrace(pw);
            }
            text.append('\n').append(trace);
        }
        return MESSAGE_PREFIX + severity + '\t' + line + '\t' + column + '\t'
            + escape(file == null ? "" : file.getAbsolutePath()) + '\t' + escape(text.toString());
    }

    /**
     * Parses a line written by {@link #formatMessage}, returning null for any other line.
     */
    static Message parseMessage(String line) {
        if (line == null || !line.startsWith(MESSAGE_PREFIX)) {
            return null;
        }
        String[] fields = line.substring(MESSAGE_PREFIX.length()).split("\t", -1);
        if (fields.length != 5) {
            return null;
        }
        String path = unescape(fields[3]);
        return new Message(parseInt(fields[0]), parseInt(fields[1]), parseInt(fields[2]),
                           path.isEmpty() ? null : new File(path), unescape(fields[4]));
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // escape the characters that separate lines and fields
    private static String escape(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
            case '\\':
                b.append("\\\\");
                break;
            case '\n':
                b.append("\\n");
                break;
            case '\r':
                b.append("\\r");
                break;
            case '\t':
                b.append("\\t");
                break;
            default:
                b.append(c);
            }
        }
        return b.toString();
    }

    private static String unescape(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                switch (n) {
                case 'n':
                    b.append('\n');
                    break;
                case 'r':
                    b.append('\r');
                    break;
                case 't':
                    b.append('\t');
                    break;
                default:
                    b.append(n);
                }
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    public void removeMessages(File file) {
    }
    public boolean isUptodate(File target, File source) {
        return false;
    }

}
