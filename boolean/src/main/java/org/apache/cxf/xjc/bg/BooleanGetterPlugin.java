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
package org.apache.cxf.xjc.bg;

import java.util.Collection;
import java.util.logging.Logger;

import org.xml.sax.ErrorHandler;

import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JType;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;


/**
 * Generate getters named getXXX() for Booleans instead of isXXX(). Useful to use generated beans with tools
 * that needs introspections, like dozer.
 */
public class BooleanGetterPlugin {

    private static final Logger LOG = Logger.getLogger(BooleanGetterPlugin.class.getName());

    public BooleanGetterPlugin() {
    }

    public String getOptionName() {
        return "Xbg";
    }

    public String getUsage() {
        return "  -Xbg                 : Generate getters methods for Booleans";
    }

    public boolean run(Outline outline, Options opt, ErrorHandler errorHandler) {
        LOG.info("Running boolean getter plugin.");
        for (ClassOutline classOutline : outline.getClasses()) {
            JDefinedClass implClass = classOutline.implClass;
            Collection<JMethod> methods = implClass.methods();
            for (JMethod method : methods) {
                if (isBooleanGetter(method)) {
                    String newName = "get" + method.name().substring(2);
                    if (implClass.getMethod(newName, new JType[0]) != null) {
                        LOG.warning("Not renaming " + implClass.fullName() + "." + method.name()
                                    + "() as " + newName + "() already exists");
                        continue;
                    }
                    LOG.info("Changing method name from " + method.name() + " to " + newName);
                    method.javadoc().add(
                                         "\nThis getter has been renamed from " + method.name() + "() to "
                                             + newName + "() by cxf-xjc-boolean plugin.");
                    method.name(newName);
                }
            }
        }
        return true;
    }

    private static boolean isBooleanGetter(JMethod method) {
        String name = method.name();
        if (!name.startsWith("is") || name.length() > 2 && Character.isLowerCase(name.charAt(2))
            || !method.params().isEmpty() || method.hasVarArgs()) {
            return false;
        }
        String type = method.type().fullName();
        return "boolean".equals(type) || "java.lang.Boolean".equals(type);
    }

}
