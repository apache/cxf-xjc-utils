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

import java.util.Collection;

import javax.xml.namespace.QName;

import org.xml.sax.ErrorHandler;

import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.model.CEnumLeafInfo;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.EnumOutline;
import com.sun.tools.xjc.outline.FieldOutline;
import com.sun.tools.xjc.outline.Outline;
import com.sun.tools.xjc.reader.xmlschema.Messages;
import com.sun.xml.xsom.XSComponent;

/**
 * @author Dawid Pytel
 */
public class JavadocInserter {

    private Outline outline;
    private Options options;

    public JavadocInserter(Outline outline, Options opt, ErrorHandler errorHandler) {
        this.outline = outline;
        this.options = opt;
    }

    /**
     * @return true if successful
     */
    public boolean addJavadocs() {
        addJavadocsToClasses();
        addJavadocsToEnums();
        return false;
    }

    private void addJavadocsToClasses() {
        for (ClassOutline classOutline : outline.getClasses()) {
            addJavadocs(classOutline);
        }
    }

    private void addJavadocs(ClassOutline classOutline) {
        FieldOutline[] declaredFields = classOutline.getDeclaredFields();
        for (FieldOutline fieldOutline : declaredFields) {
            PropertyJavadoc propertyJavadoc = new PropertyJavadoc(outline.getCodeModel(), options,
                                                                  classOutline, fieldOutline);
            propertyJavadoc.addJavadocs();
        }
    }

    private void addJavadocsToEnums() {
        Collection<EnumOutline> enums = outline.getEnums();
        for (EnumOutline enumOutline : enums) {
            addJavadoc(enumOutline);
        }
    }

    private void addJavadoc(EnumOutline enumOutline) {
        if (isCustomBindingApplied(enumOutline)) {
            return; // JAXB binding customization overwrites xsd:documentation
        }
        XSComponent schemaComponent = enumOutline.target.getSchemaComponent();
        String documentation = XSComponentHelper.getDocumentation(schemaComponent);
        if (documentation == null || "".equals(documentation)) {
            return;
        }
        enumOutline.clazz.javadoc().add(0, documentation + "\n\n");
    }

    private boolean isCustomBindingApplied(EnumOutline enumOutline) {
        CEnumLeafInfo target = enumOutline.target;
        QName typeName = target.getTypeName();
        // typeName may be null on anonymous simple types
        if (typeName == null) {
            return false;
        }
        String defaultComment = Messages.format("ClassSelector.JavadocHeading",
                typeName.getLocalPart());
        // not very clean but the only way of determining whether Javadoc
        // customization has been applied
        return !target.javadoc.startsWith(defaultComment);
    }

}
