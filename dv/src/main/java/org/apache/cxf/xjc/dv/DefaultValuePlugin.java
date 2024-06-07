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

package org.apache.cxf.xjc.dv;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;

import org.xml.sax.ErrorHandler;

import com.sun.codemodel.ClassType;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JConditional;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JDocComment;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFieldRef;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JOp;
import com.sun.codemodel.JTryBlock;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;
import com.sun.tools.xjc.BadCommandLineException;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.FieldOutline;
import com.sun.tools.xjc.outline.Outline;
import com.sun.tools.xjc.outline.PackageOutline;
import com.sun.tools.xjc.util.NamespaceContextAdapter;
import com.sun.xml.xsom.XSAttributeDecl;
import com.sun.xml.xsom.XSAttributeUse;
import com.sun.xml.xsom.XSElementDecl;
import com.sun.xml.xsom.XSParticle;
import com.sun.xml.xsom.XSTerm;
import com.sun.xml.xsom.XSType;
import com.sun.xml.xsom.XmlString;

import jakarta.xml.bind.DatatypeConverter;
import jakarta.xml.bind.annotation.adapters.HexBinaryAdapter;

/**
 * Modifies the JAXB code model to initialize fields mapped from schema elements 
 * with their default value.
 */
public class DefaultValuePlugin {
    
    private static final Logger LOG = Logger.getLogger(DefaultValuePlugin.class.getName()); //NOPMD
    
    // Known JAXB / JAXWS classes that do not have default constructors.
    private static final Set<String> KNOWN_NO_DV_CLASSES = new HashSet<>(
            Arrays.asList(
                "jakarta.xml.ws.wsaddressing.W3CEndpointReference",
                "jakarta.xml.bind.JAXBElement"
            )
        );

    private boolean complexTypes;
    private boolean active;
    
    public DefaultValuePlugin() {
    }

    public String getOptionName() {
        return "Xdv";
    }

    public String getUsage() {
        return   "  -Xdv                 : Initialize fields mapped from elements with their default values\n"
               + "  -Xdv:optional        : Initialize fields mapped from elements with their default values\n"
               + "                         for elements with minOccurs=0 but with complexTypes containing \n"
               + "                         fields with default values.";
    }

    public int parseArgument(Options opt, String[] args, int index, com.sun.tools.xjc.Plugin plugin) 
        throws BadCommandLineException, IOException {
        int ret = 0;
        
        if (args[index].startsWith("-Xdv")) {
            ret = 1;                    
            if (args[index].indexOf(":optional") != -1) {
                complexTypes = true;
            }
            if (!opt.activePlugins.contains(plugin)) {
                opt.activePlugins.add(plugin);
            }
            active = true;
        }
        return ret;
    }

    private boolean isAbstract(Outline outline, FieldOutline field) {
        for (ClassOutline classOutline : outline.getClasses()) {
            if (classOutline.implClass == field.getRawType() 
                && classOutline.implClass.isAbstract()) {
                return true;
            }
        }
        return false;
    }
    
    private boolean containsDefaultValue(Outline outline, FieldOutline field) {
        ClassOutline fClass = null;
        for (ClassOutline classOutline : outline.getClasses()) {
            if (classOutline.implClass == field.getRawType() 
                && !classOutline.implClass.isAbstract()) {
                fClass = classOutline;
                break;
            }
        }
        if (fClass == null) {
            return false;
        }
        for (FieldOutline f : fClass.getDeclaredFields()) {
            if (f.getPropertyInfo().getSchemaComponent() instanceof XSParticle) {
                XSParticle particle = (XSParticle)f.getPropertyInfo().getSchemaComponent();
                XSTerm term = particle.getTerm();
                if (term.isElementDecl() && term.asElementDecl().getDefaultValue() != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isElementRequired(XSParticle particle) {
        return particle != null && getMinOccurs(particle) != 0 && getMaxOccurs(particle) == 1;
    }
    
    private int getMinOccurs(XSParticle particle) {
        try {
            Number o = (Number)particle.getClass().getMethod("getMinOccurs").invoke(particle);
            return o.intValue(); 
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    private int getMaxOccurs(XSParticle particle) {
        try {
            Number o = (Number)particle.getClass().getMethod("getMaxOccurs").invoke(particle);
            return o.intValue(); 
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public boolean run(Outline outline, Options opt, ErrorHandler errorHandler) {
        if (!active) {
            return true;
        }
        LOG.fine("Running default value plugin.");
        for (ClassOutline co : outline.getClasses()) {
            for (FieldOutline f : co.getDeclaredFields()) {

                // Use XML schema object model to determine if field is mapped
                // from an element (attributes default values are handled
                // natively) and get its default value.
                XmlString xmlDefaultValue = null;
                XSType xsType = null;
                boolean isElement = false;
                boolean isRequiredAttr = true;
                XSParticle particle = null;
                if (f.getPropertyInfo().getSchemaComponent() instanceof XSParticle) {
                    particle = (XSParticle)f.getPropertyInfo().getSchemaComponent();
                    XSTerm term = particle.getTerm();

                    if (term.isElementDecl()) {
                        XSElementDecl element = particle.getTerm().asElementDecl();
                        xmlDefaultValue = element.getDefaultValue();
                        xsType = element.getType();
                        isElement = true;
                    }
                } else if (f.getPropertyInfo().getSchemaComponent() instanceof XSAttributeUse) {
                    XSAttributeUse attributeUse = (XSAttributeUse)f.getPropertyInfo().getSchemaComponent();
                    XSAttributeDecl decl = attributeUse.getDecl();
                    xmlDefaultValue = decl.getDefaultValue();
                    xsType = decl.getType();
                    isRequiredAttr = attributeUse.isRequired();
                }

                if (xsType != null 
                    && xsType.isComplexType()
                    && !isAbstract(outline, f)
                    && ((complexTypes && containsDefaultValue(outline, f)) 
                        || isElementRequired(particle))) {
                    String varName = f.getPropertyInfo().getName(false);
                    JFieldVar var = co.implClass.fields().get(varName);
                    final JType rawType = f.getRawType();
                    if (var != null && !KNOWN_NO_DV_CLASSES.contains(rawType.erasure().fullName())) {
                        if (rawType instanceof JClass) {
                            final JClass jclazz = (JClass) rawType;
                            if (!jclazz.isAbstract() && !jclazz.isInterface()) {
                                var.init(JExpr._new(rawType));
                            }
                        } else {
                            var.init(JExpr._new(rawType));
                        }
                    }
                }

                JExpression dvExpr = null;
                if (null != xmlDefaultValue && null != xmlDefaultValue.value) {
                    dvExpr = getDefaultValueExpression(f, co, outline, xsType, isElement,
                                                       xmlDefaultValue, false);
                }
                 
                if (null == dvExpr
                    && !isElement 
                    && !isRequiredAttr
                    && xsType != null && xsType.getOwnerSchema() != null) {
                    //attribute, may still be able to convert it, but need to do
                    //a bunch more checks and changes to setters and isSet and such

                    dvExpr = 
                        getDefaultValueExpression(f, co, outline, xsType, false, 
                                                  xmlDefaultValue, true);
                    
                    if (dvExpr != null) {
                        updateSetter(co, f, co.implClass);
                        updateGetter(co, f, co.implClass, dvExpr, true);
                    } else {
                        JType type = f.getRawType();
                        String typeName = type.fullName();
                        if ("javax.xml.datatype.Duration".equals(typeName)) {
                            updateDurationGetter(co, f, co.implClass, xmlDefaultValue, outline);
                        }
                    }
                } else if (null == dvExpr) {                    
                    JType type = f.getRawType();
                    String typeName = type.fullName();
                    if ("javax.xml.datatype.Duration".equals(typeName)) {
                        updateDurationGetter(co, f, co.implClass, xmlDefaultValue, outline);
                    }
                } else {
                    updateGetter(co, f, co.implClass, dvExpr, false);                    
                }
            }
        }
        
        for (PackageOutline po :outline.getAllPackageContexts()) {
            //also fixup some unecessary casts
            JDefinedClass cls = po.objectFactoryGenerator().getObjectFactory();
            for (JMethod m : cls.methods()) {
                String tn = m.type().fullName();
                if (tn.startsWith("jakarta.xml.bind.JAXBElement<java.util.List<") 
                    || tn.startsWith("jakarta.xml.bind.JAXBElement<byte[]>")) {
                    JBlock b = m.body();
                    
                    for (Object o : b.getContents()) {
                        try {
                            Field f = o.getClass().getDeclaredField("expr");
                            f.setAccessible(true);
                            JInvocation ji = (JInvocation)f.get(o);
                            
                            f = JInvocation.class.getDeclaredField("args");
                            f.setAccessible(true);
                            @SuppressWarnings("unchecked")
                            List<JExpression> args = (List<JExpression>)f.get(ji);
                            
                            JExpression cast = args.get(args.size() - 1);
                            if ("JCast".equals(cast.getClass().getSimpleName())) {
                                f = cast.getClass().getDeclaredField("object");
                                f.setAccessible(true);
                                JExpression exp = (JExpression)f.get(cast);
                                args.remove(args.size() - 1);
                                args.add(exp);
                            }
                        } catch (Throwable t) {
                            //ignore
                        }
                    }
                }
            }
        }

        return true;
    }
    
    
    private void updateDurationGetter(ClassOutline co, FieldOutline fo, JDefinedClass dc,
                                      XmlString xmlDefaultValue, Outline outline) {
        String fieldName = fo.getPropertyInfo().getName(false);

        String getterName = "get" + fo.getPropertyInfo().getName(true);

        JMethod method = dc.getMethod(getterName, new JType[0]);
        JDocComment doc = method.javadoc();
        int mods = method.mods().getValue();
        JType mtype = method.type();

        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Updating getter: " + getterName);
        }
        // remove existing method and define new one

        dc.methods().remove(method);

        method = dc.method(mods, mtype, getterName);
        method.javadoc().append(doc);

        JFieldRef fr = JExpr.ref(fieldName);
        if (xmlDefaultValue != null) {
            JExpression test = JOp.eq(JExpr._null(), fr);
            JConditional jc =  method.body()._if(test);
            JTryBlock b = jc._then()._try();
            b.body()._return(outline.getCodeModel().ref(DatatypeFactory.class)
                .staticInvoke("newInstance").invoke("newDuration").arg(JExpr.lit(xmlDefaultValue.value)));
            b._catch(outline.getCodeModel().ref(DatatypeConfigurationException.class));
            method.body()._return(fr);
        } else {
            method.body()._return(fr);
        }

    }

    JExpression getDefaultValueExpression(FieldOutline f,
                                          ClassOutline co,
                                          Outline outline,
                                          XSType xsType,
                                          boolean isElement,
                                          XmlString xmlDefaultValue,
                                          boolean unbox) {
        JType type = f.getRawType();
        String typeName = type.fullName();
        String defaultValue = xmlDefaultValue == null ? null : xmlDefaultValue.value;
        if (defaultValue == null) {
            return null;
        }

        JExpression dv = null;
        
        if ("java.lang.Boolean".equals(typeName) && isElement) {
            dv = JExpr.direct(Boolean.valueOf(defaultValue) ? "Boolean.TRUE" : "Boolean.FALSE");
        } else if ("java.lang.Byte".equals(typeName) && isElement) {
            dv = JExpr._new(type)
                .arg(JExpr.cast(type.unboxify(),
                    JExpr.lit(Byte.parseByte(defaultValue))));
        } else if ("java.lang.Double".equals(typeName) && isElement) {
            dv = JExpr._new(type)
                .arg(JExpr.lit(Double.parseDouble(defaultValue)));
        } else if ("java.lang.Float".equals(typeName) && isElement) {
            dv = JExpr._new(type)
                 .arg(JExpr.lit(Float.parseFloat(defaultValue)));
        } else if ("java.lang.Integer".equals(typeName) && isElement) {
            dv = JExpr._new(type)
                .arg(JExpr.lit(Integer.parseInt(defaultValue)));
        } else if ("java.lang.Long".equals(typeName) && isElement) {
            dv = JExpr._new(type)
                .arg(JExpr.lit(Long.parseLong(defaultValue)));
        } else if ("java.lang.Short".equals(typeName) && isElement) {
            dv = JExpr._new(type)
                .arg(JExpr.cast(type.unboxify(),
                    JExpr.lit(Short.parseShort(defaultValue))));
        } else if ("java.lang.String".equals(type.fullName()) && isElement) {
            dv = JExpr.lit(defaultValue);
        } else if ("java.math.BigInteger".equals(type.fullName()) && isElement) {
            dv = JExpr._new(type).arg(JExpr.lit(defaultValue));
        } else if ("java.math.BigDecimal".equals(type.fullName()) && isElement) {
            dv = JExpr._new(type).arg(JExpr.lit(defaultValue));
        } else if ("byte[]".equals(type.fullName()) && xsType.isSimpleType() && isElement) {
            while (!"anySimpleType".equals(xsType.getBaseType().getName())) {
                xsType = xsType.getBaseType();
            }
            if ("base64Binary".equals(xsType.getName())) {
                dv = outline.getCodeModel().ref(DatatypeConverter.class)
                   .staticInvoke("parseBase64Binary").arg(defaultValue);
            } else if ("hexBinary".equals(xsType.getName())) {
                dv = JExpr._new(outline.getCodeModel().ref(HexBinaryAdapter.class))
                    .invoke("unmarshal").arg(defaultValue);
            }
        } else if ("javax.xml.namespace.QName".equals(typeName)) {
            NamespaceContext nsc = new NamespaceContextAdapter(xmlDefaultValue);
            QName qn = DatatypeConverter.parseQName(xmlDefaultValue.value, nsc);
            dv = JExpr._new(outline.getCodeModel().ref(QName.class))
                .arg(qn.getNamespaceURI())
                .arg(qn.getLocalPart())
                .arg(qn.getPrefix());
        } else if ("javax.xml.datatype.Duration".equals(typeName)) {
            dv = null;
        } else if (type instanceof JDefinedClass) {
            JDefinedClass cls = (JDefinedClass)type;
            if (cls.getClassType() == ClassType.ENUM) {
                dv = cls.staticInvoke("fromValue").arg(defaultValue);
            }
        } else if (unbox) {
            typeName = type.unboxify().fullName();
            if ("int".equals(typeName)) {
                dv = JExpr.lit(Integer.parseInt(defaultValue));
            } else if ("long".equals(typeName)) {
                dv = JExpr.lit(Long.parseLong(defaultValue));
            } else if ("short".equals(typeName)) {
                dv = JExpr.lit(Short.parseShort(defaultValue));
            } else if ("boolean".equals(typeName)) {
                dv = JExpr.lit(Boolean.parseBoolean(defaultValue));
            } else if ("double".equals(typeName)) {
                dv = JExpr.lit(Double.parseDouble(defaultValue));
            } else if ("float".equals(typeName)) {
                dv = JExpr.lit(Float.parseFloat(defaultValue));
            } else if ("byte".equals(typeName)) {
                dv = JExpr.lit(Byte.parseByte(defaultValue));
            } else {
                dv = getDefaultValueExpression(f,
                                               co,
                                               outline,
                                               xsType,
                                               true,
                                               xmlDefaultValue,
                                               false);
            }
        }
        return dv;
    }
    
    private void updateGetter(ClassOutline co, FieldOutline fo, 
                              JDefinedClass dc, JExpression dvExpr,
                              boolean remapRet) {

        String fieldName = fo.getPropertyInfo().getName(false);
        JType type = fo.getRawType();
        String typeName = type.fullName();

        String getterName = ("java.lang.Boolean".equals(typeName) ? "is" : "get")
                            + fo.getPropertyInfo().getName(true);

        JMethod method = dc.getMethod(getterName, new JType[0]);
        JDocComment doc = method.javadoc();
        int mods = method.mods().getValue();
        JType mtype = method.type();
        if (remapRet) {
            mtype = mtype.unboxify();
        }

        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Updating getter: " + getterName);
        }
        // remove existing method and define new one
        dc.methods().remove(method);

        method = dc.method(mods, mtype, getterName);
        method.javadoc().append(doc);

        JFieldRef fr = JExpr.ref(fieldName);
        if (dvExpr != null) {
            JExpression test = JOp.eq(JExpr._null(), fr);
            JConditional jc =  method.body()._if(test);
            jc._then()._return(dvExpr);
        }
        method.body()._return(fr);
    }
    private void updateSetter(ClassOutline co, FieldOutline fo, 
                              JDefinedClass dc) {

        String fieldName = fo.getPropertyInfo().getName(false);
        JType type = fo.getRawType();
        String typeName = type.fullName();

        String getterName = ("java.lang.Boolean".equals(typeName) ? "is" : "get")
                            + fo.getPropertyInfo().getName(true);
        JMethod method = dc.getMethod(getterName, new JType[0]);
        JType mtype = method.type();
        String setterName = "set" + fo.getPropertyInfo().getName(true);
        method = dc.getMethod(setterName, new JType[] {mtype});
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Updating setter: " + setterName);
        }
        if (method == null) {
            return;
        }
        JDocComment doc = method.javadoc();
        // remove existing method and define new one
        dc.methods().remove(method);

        int mods = method.mods().getValue();
        mtype = mtype.unboxify();
        method = dc.method(mods, method.type(), setterName);
        
        method.javadoc().append(doc);
        JVar var = method.param(mtype, "value");

        JFieldRef fr = JExpr.ref(fieldName);
        method.body().assign(fr, var);
        
        JMethod oldMethod = dc.getMethod("unset" + fo.getPropertyInfo().getName(true), new JType[0]);
        if (oldMethod != null) {
            dc.methods().remove(oldMethod);
        }
        method = dc.method(mods, method.type(), "unset" + fo.getPropertyInfo().getName(true));
        method.body().assign(fr, JExpr._null());
        
        method = dc.getMethod("isSet" + fo.getPropertyInfo().getName(true), new JType[0]);
        if (method != null) {
            //move to end
            dc.methods().remove(method);
            dc.methods().add(method);
        }
        
    }

    public void onActivated(Options opts) {
        active = true;
    }
}
