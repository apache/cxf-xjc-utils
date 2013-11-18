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
package org.apache.cxf.xjc.javadoc;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;

import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.Plugin;
import com.sun.tools.xjc.outline.Outline;

/**
 * Generates Javadocs based on xsd:documentation.
 * 
 * @author Dawid Pytel
 */
public class JavadocPlugin extends Plugin {

    /*
     * (non-Javadoc)
     * @see com.sun.tools.xjc.Plugin#getOptionName()
     */
    @Override
    public String getOptionName() {
        return "Xjavadoc";
    }

    /*
     * (non-Javadoc)
     * @see com.sun.tools.xjc.Plugin#getUsage()
     */
    @Override
    public String getUsage() {
        return "  -Xjavadoc            :  Generates Javadocs based on xsd:documentation.";
    }

    /*
     * (non-Javadoc)
     * @see com.sun.tools.xjc.Plugin#run(com.sun.tools.xjc.outline.Outline, com.sun.tools.xjc.Options,
     * org.xml.sax.ErrorHandler)
     */
    @Override
    public boolean run(Outline outline, Options opt, ErrorHandler errorHandler) throws SAXException {
        JavadocInserter javadocInserter = new JavadocInserter(outline, opt, errorHandler);
        return javadocInserter.addJavadocs();
    }

}
