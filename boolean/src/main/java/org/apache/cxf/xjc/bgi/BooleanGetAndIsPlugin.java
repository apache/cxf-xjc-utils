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
package org.apache.cxf.xjc.bgi;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import org.xml.sax.ErrorHandler;

import com.sun.codemodel.JExpr;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;

/**
 * Generate getters named getXXX() and isXXX().
 */
public class BooleanGetAndIsPlugin {

    private static final Logger LOG = Logger.getLogger(BooleanGetAndIsPlugin.class.getName());
    private static final String IS_PREFIX = "is";
    
    public BooleanGetAndIsPlugin() {
    }

    public String getOptionName() {
        return "Xbgi";
    }

    public String getUsage() {
        return "  -Xbgi                 : Generate getXXX and isXXX methods for Booleans";
    }

    public boolean run(Outline outline, Options opt, ErrorHandler errorHandler) {
        for (ClassOutline classOutline : outline.getClasses()) {
            processClass(classOutline);
        }
        return true;
    }

    private void processClass(ClassOutline clazz) {
        Collection<JMethod> methods = clazz.implClass.methods();
        Map<String, JMethod> methodsToAdd = new HashMap<String, JMethod>();
        for (JMethod method : methods) {
            if (method.name().startsWith(IS_PREFIX) && requiresGetter(methods, method)) {
                methodsToAdd.put(method.name(), method);
            }
        }
        
        Iterator<Entry<String, JMethod>> todo = methodsToAdd.entrySet().iterator();
        while (todo.hasNext()) {
            JMethod isMethod = todo.next().getValue();
            String newName = "get" + isMethod.name().substring(2);
            LOG.info("Adding method " + newName);
            JMethod newMethod = clazz.implClass.method(JMod.PUBLIC, isMethod.type(), newName);
            newMethod.body()._return(JExpr.invoke(isMethod));
        }
    }
    
    private boolean requiresGetter(Collection<JMethod> methods, JMethod method) {
        String newName = "get" + method.name().substring(2);
        // Check if already exists.
        for (JMethod cursor : methods) {
            if (newName.equals(cursor.name())) {
                return false;
            }
        }
        return true;
    }
}
