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

import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDocComment;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JType;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.model.CPropertyInfo;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.FieldOutline;
import com.sun.xml.xsom.XSAttributeUse;
import com.sun.xml.xsom.XSComponent;
import com.sun.xml.xsom.XSParticle;

public class PropertyJavadoc {

    private JCodeModel codeModel;

    private Options options;

    private ClassOutline classOutline;

    private FieldOutline fieldOutline;

    public PropertyJavadoc(JCodeModel codeModel, Options options, ClassOutline classOutline,
                           FieldOutline fieldOutline) {
        this.codeModel = codeModel;
        this.options = options;
        this.classOutline = classOutline;
        this.fieldOutline = fieldOutline;
    }

    public void addJavadocs() {
        CPropertyInfo propertyInfo = fieldOutline.getPropertyInfo();
        if (propertyInfo == null) {
            return;
        }
        if (propertyInfo.javadoc.length() > 0) {
            return; // JAXB binding customization overwrites xsd:documentation
        }
        XSComponent component = getDocumentedComponent(propertyInfo);

        String documentation = XSComponentHelper.getDocumentation(component);
        if (documentation == null || "".equals(documentation.trim())) {
            return;
        }
        setJavadoc(documentation.replaceAll("^\\s+|\\s+$|\\s*(\n)\\s*|(\\s)\\s*", "$1$2"));
    }

    private XSComponent getDocumentedComponent(CPropertyInfo propertyInfo) {
        XSComponent schemaComponent = propertyInfo.getSchemaComponent();
        if (schemaComponent instanceof XSParticle) {
            return ((XSParticle)schemaComponent).getTerm();
        } else if (schemaComponent instanceof XSAttributeUse) {
            return ((XSAttributeUse)schemaComponent).getDecl();
        } else {
            return null;
        }
    }

    private void setJavadoc(String documentation) {
        setJavadocToField(documentation);
        setJavadocToGetter(documentation);
        setSeeTagToSetter();
    }

    private void setJavadocToField(String documentation) {
        JFieldVar fieldVar = classOutline.implClass.fields().get(fieldOutline.getPropertyInfo()
                                                                     .getName(false));
        if (fieldVar == null) {
            return;
        }
        fieldVar.javadoc().append(documentation);
    }

    private void setJavadocToGetter(String documentation) {
        String getterMethod = getGetterMethod();
        JMethod getter = MethodHelper.findMethod(classOutline, getterMethod);
        JDocComment javadoc = getter.javadoc();
        if (javadoc.size() != 0) {
            documentation = "\n<p>\n" + documentation;
        }
        javadoc.add(javadoc.size(), documentation); // add comment as last
                                                    // non-tag element
    }

    private void setSeeTagToSetter() {
        JMethod setterMethod = MethodHelper.findMethod(classOutline, "set"
                                                                     + fieldOutline.getPropertyInfo()
                                                                         .getName(true));
        if (setterMethod == null) {
            return;
        }
        setterMethod.javadoc().addXdoclet("see #" + getGetterMethod() + "()");
    }

    private String getGetterMethod() {
        JType type = fieldOutline.getRawType();
        if (options.enableIntrospection) {
            return ((type.isPrimitive() && type.boxify().getPrimitiveType() == codeModel.BOOLEAN)
                ? "is" : "get") + fieldOutline.getPropertyInfo().getName(true);
        }
        return (type.boxify().getPrimitiveType() == codeModel.BOOLEAN ? "is" : "get")
               + fieldOutline.getPropertyInfo().getName(true);
    }

}
