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

package org.apache.cxf.xjc.bug986;


import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.xml.bind.annotation.XmlSchemaType;

import org.xml.sax.ErrorHandler;

import com.sun.codemodel.JAnnotationUse;
import com.sun.codemodel.JAnnotationValue;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JFormatter;
import com.sun.codemodel.JVar;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.Plugin;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;

/**
 * Modifies the JAXB code model to handle package naming that run into:
 * https://jaxb.dev.java.net/issues/show_bug.cgi?id=671
 */
public class Bug986Plugin {
    private static final Logger LOG = Logger.getLogger(Bug986Plugin.class.getName()); //NOPMD

    final Plugin plugin;

    public Bug986Plugin(Plugin p) {
        plugin = p;
    }
    
    
    public String getOptionName() {
        return "Xbug986";
    }

    public String getUsage() {
        return "  -Xbug986             : Activate plugin remove XmlSchemaType(anySimpleType)"
            + " from fields that shouldn't have it.";
    }

    public boolean run(Outline outline, Options opt, ErrorHandler errorHandler) {
        // kind of a bogus thing to have to do to workaround bug:
        // https://java.net/jira/browse/JAXB-986
        LOG.fine("Running Bug986Plugin plugin.");
        for (ClassOutline classOutline : outline.getClasses()) {
            Map<String, JFieldVar> fields = classOutline.implClass.fields();
            for (JFieldVar field : fields.values()) {
                Collection<JAnnotationUse> annotations = getAnnotations(field);
                List<JAnnotationUse> toRemove = new ArrayList<JAnnotationUse>();
                for (JAnnotationUse j : annotations) {
                    if (XmlSchemaType.class.getName().equals(getAnnotationClass(j).fullName())) {
                        JAnnotationValue st = getAnnotationMember(j, "name");
                        StringWriter sw = new StringWriter();
                        st.generate(new JFormatter(sw));
                        if (sw.toString().equals("\"anySimpleType\"")) {
                            if (field.type().fullName().startsWith("java.util.List")) {
                                //if it's a list of non-string types, we have to remove
                                if (!!field.type().fullName().contains("<java.lang.String>")) {
                                    toRemove.add(j);
                                }
                            } else if (!"java.lang.String".equals(field.type().fullName())) {
                                //if it's not a list and it's not a string, we have to remove
                                toRemove.add(j);
                            }
                        }
                    }
                }
                for (JAnnotationUse j : toRemove) {
                    annotations.remove(j);
                }
            }
        }
        return true;
    }
    
    private JAnnotationValue getAnnotationMember(JAnnotationUse ju, String name) {
        try {
            Field f = JAnnotationUse.class.getDeclaredField("memberValues");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, JAnnotationValue> memberValues = (Map<String, JAnnotationValue>)f.get(ju);
            if (memberValues == null) {
                return null;
            }
            return memberValues.get(name);
        } catch (Throwable t) {
            //ignore for now
            t.printStackTrace();
        }
        return null;
    }
    
    private JClass getAnnotationClass(JAnnotationUse ju) {
        try {
            Field f = JAnnotationUse.class.getDeclaredField("clazz");
            f.setAccessible(true);
            return (JClass)f.get(ju);
        } catch (Throwable t) {
            //ignore for now
            t.printStackTrace();
        }
        return null;
    }
    
    @SuppressWarnings("unchecked")
    private List<JAnnotationUse> getAnnotations(JFieldVar field) {
        try {
            Field f = JVar.class.getDeclaredField("annotations");
            f.setAccessible(true);
            List<?> anns = (List<?>)f.get(field);
            if (anns == null) {
                anns = Collections.emptyList();
            }
            return (List<JAnnotationUse>)anns;
        } catch (Throwable t) {
            //ignore for now
            t.printStackTrace();
        }
        return Collections.emptyList();
    }
}
