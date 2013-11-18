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

import com.sun.codemodel.JMethod;
import com.sun.tools.xjc.outline.ClassOutline;

public final class MethodHelper {

    private MethodHelper() {
        // no constructor for helper class
    }

    /**
     * Find method in given class with given method name
     * 
     * @param classOutline
     * @param methodName
     * @return method in given class with given method name
     */
    public static JMethod findMethod(ClassOutline classOutline, String methodName) {
        Collection<JMethod> methods = classOutline.implClass.methods();
        for (JMethod method : methods) {
            if (method.name().equals(methodName)) {
                return method;
            }
        }
        return null;
    }

}
