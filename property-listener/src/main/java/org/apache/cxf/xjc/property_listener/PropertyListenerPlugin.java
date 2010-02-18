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

package org.apache.cxf.xjc.property_listener;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.logging.Logger;

import javax.xml.bind.annotation.XmlTransient;

import org.xml.sax.ErrorHandler;

import com.sun.codemodel.JAssignment;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFieldRef;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;

/**
 * Modifies the JAXB code model to add a PropertyChangeListener to the 
 * setter methods
 */
public class PropertyListenerPlugin {
    
    private static final Logger LOG = Logger.getLogger(PropertyListenerPlugin.class.getName()); //NOPMD
    
    public PropertyListenerPlugin() {
    }

    public String getOptionName() {
        return "Xproperty-listener";
    }

    public String getUsage() {
        return "  -Xproperty-listener    : Adds a PropertyChangeListener to all the set methods";
    }


    public boolean run(Outline outline, Options opt, ErrorHandler errorHandler) {
        LOG.fine("Running property-listener plugin.");
        for (ClassOutline co : outline.getClasses()) {
            if (co.getDeclaredFields().length == 0) {
                continue;
            }            
            
            //add listener support
            JType listenerType = co.parent().getCodeModel()._ref(PropertyChangeSupport.class);
            JFieldVar newVar = co.implClass.field(Modifier.PRIVATE, 
                                                  listenerType, 
                                                  "propertyListener", 
                                                  JExpr._new(listenerType).arg(JExpr._this()));
            newVar.annotate(XmlTransient.class);
            
            JMethod method = co.implClass.method(Modifier.PUBLIC, Void.TYPE, "addPropertyChangeListener");
            JVar listener = method.param(PropertyChangeListener.class, "listener");
            method.body().invoke(newVar, "addPropertyChangeListener").arg(listener);
            
            method = co.implClass.method(Modifier.PUBLIC, Void.TYPE, "removePropertyChangeListener");
            listener = method.param(PropertyChangeListener.class, "listener");
            method.body().invoke(newVar, "removePropertyChangeListener").arg(listener);
            
            //add firePropertyChange to set methods
            List<JMethod> methods = (List<JMethod>)co.implClass.methods();
            for (int x = 0; x < methods.size(); x++) {
                JMethod m = methods.get(x);
                if (m.name().startsWith("set")) {
                    m.body().pos(0);
                    List<Object> contents = m.body().getContents();
                    JFieldRef target = null;
                    String targetName = null;
                    JExpression value = null;
                    for (Object o : contents) {
                        if (o instanceof JAssignment) {
                            JAssignment jass = (JAssignment)o;
                            try {
                                Field f = jass.getClass().getDeclaredField("lhs");
                                f.setAccessible(true);
                                Object t = f.get(jass);
                                if (t instanceof JFieldRef) {
                                    f = jass.getClass().getDeclaredField("rhs");
                                    f.setAccessible(true);
                                    value = (JExpression)f.get(jass);
                                    target = (JFieldRef)t;
                                }
                            } catch (Throwable t) {
                                //ignore
                            }
                        }
                    }
                    if (target != null) {
                        try {
                            targetName = getName(target);
                            
                            JFieldVar field = co.implClass.fields().get(targetName);
                            
                            if (value instanceof JVar) {
                                JVar var = (JVar)value;
                                JType t = var.type();
                                if ("int".equals(t.fullName())
                                    && !"int".equals(field.type().fullName())) {
                                    value = JExpr.cast(co.parent().getCodeModel()._ref(Integer.class),
                                                       value);
                                } else if ("boolean".equals(t.fullName())
                                    && !"boolean".equals(field.type().fullName())) {
                                    value = JExpr.cast(co.parent().getCodeModel()._ref(Boolean.class),
                                                       value);
                                } 
                            }
                            
                            m.body().invoke(newVar, "firePropertyChange").arg(targetName)
                                .arg(target).arg(value);
                        } catch (Throwable t) {
                            //ignore
                            t.printStackTrace();
                        }
                    }
                }
                
            }
                    
        }
        return true;
    }
    
    String getName(JFieldRef ref) {
        try {
            Field f = ref.getClass().getDeclaredField("name");
            f.setAccessible(true);
            String targetName = (String)f.get(ref);
            if (targetName == null) {
                f = ref.getClass().getDeclaredField("var");
                f.setAccessible(true);
                JVar v = (JVar)f.get(ref);
                targetName = v.name();
            }
            return targetName;
        } catch (Throwable t) {
            //ignore
        }
        return null;
    }

}
