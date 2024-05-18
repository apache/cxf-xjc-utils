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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.sun.tools.xjc.BadCommandLineException;
import com.sun.tools.xjc.Driver;
import com.sun.tools.xjc.Plugin;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.apache.cxf.xjc.javadoc.JavadocTestHelper.containsTag;
import static org.apache.cxf.xjc.javadoc.JavadocTestHelper.javadocContains;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;

/**
 * End-to-end integration test of JavadocPlugin
 */
public class JavadocPluginTest extends Assert {

    private static final String PACKAGE_DIR = "org/example/xjc_javadoc_plugin";
    private static final String OUTPUT_DIR = "target";

    @Before
    public void deleteAllGeneratedFiles() throws IOException {
        File generatedFilesDir = new File(OUTPUT_DIR, PACKAGE_DIR);
        FileUtils.deleteDirectory(generatedFilesDir);
    }

    @Test
    public void pluginShouldBeLoaded() throws Exception {
        try {
            Driver.run(new String[] {}, System.out, System.out);
            fail("Expected xjc to fail with BadCommandLineException");
        } catch (BadCommandLineException e) {
            for (Plugin p : e.getOptions().getAllPlugins()) {
                if (p instanceof JavadocPlugin) {
                    return;
                }
            }
            fail("Did not load plugin");
        }
    }

    @Test
    public void testComplexTypeWithDocumentedProperties() throws Exception {
        String fileName = "complexTypeWithDocumentedProperties.xsd";

        assertProcessedSuccessful(fileName);

        CompilationUnit compilationUnit = parseSourceFile("ComplexTypeWithDocumentedProperties.java");
        Javadoc fieldJavadoc = getJavadocOfField(compilationUnit, "documentedElement");
        assertThat(fieldJavadoc, javadocContains("Some documentation of element"));

        Javadoc getterJavadoc = getJavadocOfMethod(compilationUnit, "getDocumentedElement");
        assertThat(getterJavadoc, javadocContains("Some documentation of element"));

        Javadoc setterJavadoc = getJavadocOfMethod(compilationUnit, "setDocumentedElement");
        assertThat(setterJavadoc, containsTag("@see", "#getDocumentedElement()"));
    }

    @Test
    public void testComplexTypeWithoutProperties() throws Exception {
        String fileName = "complexTypeWithoutProperties.xsd";
        assertProcessedSuccessful(fileName);
    }

    @Test
    public void testDocumentedEnum() throws Exception {
        String fileName = "enumDocumented.xsd";
        assertProcessedSuccessful(fileName);

        CompilationUnit compilationUnit = parseSourceFile("EnumDocumented.java");
        EnumDeclaration type = getTopLevelEnum(compilationUnit);
        assertThat(type.getJavadoc(), javadocContains("Documentation of enumDocumented"));
    }

    @Test
    public void testDocumentationOnPropertiesIsOverwrittenByJAXBBindings() throws Exception {
        String fileName = "complexTypeWithDocumentedProperties.xsd";

        assertProcessedSuccessful(fileName, "-b",
                                  getAbsolutePath("complexTypeWithDocumentedProperties-javadoc-bindings.xjb"));

        CompilationUnit compilationUnit = parseSourceFile("ComplexTypeWithDocumentedProperties.java");
        Javadoc fieldJavadoc = getJavadocOfField(compilationUnit, "documentedElement");
        assertThat(fieldJavadoc, not(javadocContains("Some documentation of element")));

        Javadoc getterJavadoc = getJavadocOfMethod(compilationUnit, "getDocumentedElement");
        assertThat(getterJavadoc, javadocContains("Documentation from JAXB binding customization"));
        assertThat(getterJavadoc, not(javadocContains("Some documentation of element")));
    }

    @Test
    public void testDocumentationOnEnumsIsOverwrittenByJAXBBindings() throws Exception {
        String fileName = "enumDocumented.xsd";
        assertProcessedSuccessful(fileName, "-b", getAbsolutePath("enumDocumented-javadoc-bindings.xjb"));

        CompilationUnit compilationUnit = parseSourceFile("EnumDocumented.java");
        EnumDeclaration type = getTopLevelEnum(compilationUnit);
        assertThat(type.getJavadoc(), not(javadocContains("Documentation of enumDocumented")));
        assertThat(type.getJavadoc(), javadocContains("Documentation from JAXB binding customization"));
    }

    @Test
    public void testComplexTypeWithDocumentedAttribute() throws Exception {
        String fileName = "complexTypeWithDocumentedAttribute.xsd";

        assertProcessedSuccessful(fileName);

        CompilationUnit compilationUnit = parseSourceFile("ComplexTypeWithDocumentedAttribute.java");
        Javadoc fieldJavadoc = getJavadocOfField(compilationUnit, "documentedAttribute");
        assertThat(fieldJavadoc, javadocContains("Documentation of attribute"));

        Javadoc getterJavadoc = getJavadocOfMethod(compilationUnit, "getDocumentedAttribute");
        assertThat(getterJavadoc, javadocContains("Documentation of attribute"));
    }

    @Test
    public void testAnonymousEnum() throws Exception {
        String fileName = "anonymousEnum.xsd";
        assertProcessedSuccessful(fileName, "-b", getAbsolutePath("anonymousEnum-javadoc-bindings.xjb"));

        CompilationUnit compilationUnit = parseSourceFile("AnonymousTypesafeEnumClass.java");
        Javadoc topLevelTypeJavadoc = getTopLevelEnum(compilationUnit).getJavadoc();
        assertThat(topLevelTypeJavadoc, javadocContains("Documentation of anonymous enum simpleType"));
    }

    private void assertProcessedSuccessful(String fileName, String... params) throws Exception {
        String xsdPath = getAbsolutePath(fileName);
        List<String> args = new ArrayList<String>(Arrays.asList(xsdPath, "-Xjavadoc", "-d", OUTPUT_DIR));
        args.addAll(Arrays.asList(params));
        int result = runXjc(args);

        assertThat(result, is(0));
    }

    private String getAbsolutePath(String fileName) {
        return new File("src/test/resources", fileName).getAbsolutePath();
    }

    private int runXjc(List<String> args) throws Exception {
        return Driver.run(args.toArray(new String[0]), System.out, System.out);
    }

    private Javadoc getJavadocOfField(CompilationUnit compilationUnit, String fieldName) {
        TypeDeclaration type = getTopLevelType(compilationUnit);
        FieldDeclaration[] fields = type.getFields();
        FieldDeclaration field = findField(fields, fieldName);
        return field.getJavadoc();
    }

    private Javadoc getJavadocOfMethod(CompilationUnit compilationUnit, String methodName) {
        TypeDeclaration type = getTopLevelType(compilationUnit);
        MethodDeclaration[] methods = type.getMethods();
        MethodDeclaration method = findMethod(methods, methodName);
        return method.getJavadoc();
    }

    private CompilationUnit parseSourceFile(String fileName) throws IOException, FileNotFoundException {
        FileReader inputFile = new FileReader(new File(OUTPUT_DIR + "/" + PACKAGE_DIR, fileName));
        char[] classChars = IOUtils.toCharArray(inputFile);
        inputFile.close();
        ASTParser parser = ASTParser.newParser(AST.JLS3);
        @SuppressWarnings("rawtypes")
        Map options = JavaCore.getOptions();
        JavaCore.setComplianceOptions(JavaCore.VERSION_1_5, options);
        parser.setCompilerOptions(options);
        parser.setSource(classChars);
        return (CompilationUnit)parser.createAST(null);
    }

    private FieldDeclaration findField(FieldDeclaration[] fields, String fieldName) {
        for (FieldDeclaration field : fields) {
            VariableDeclarationFragment fragment = (VariableDeclarationFragment)field.fragments().get(0);
            String identifier = fragment.getName().getIdentifier();
            if (identifier.equals(fieldName)) {
                return field;
            }
        }
        fail("Expected to find field: " + fieldName);
        return null; // never reached
    }

    private MethodDeclaration findMethod(MethodDeclaration[] methods, String methodName) {
        for (MethodDeclaration method : methods) {
            if (methodName.equals(method.getName().getIdentifier())) {
                return method;
            }
        }
        fail("Expected to find method: " + methodName);
        return null; // never reached
    }

    private TypeDeclaration getTopLevelType(CompilationUnit compilationUnit) {
        return (TypeDeclaration)getTopLevelDeclaration(compilationUnit);
    }

    private EnumDeclaration getTopLevelEnum(CompilationUnit compilationUnit) {
        return (EnumDeclaration)getTopLevelDeclaration(compilationUnit);
    }

    private Object getTopLevelDeclaration(CompilationUnit compilationUnit) {
        return compilationUnit.types().get(0);
    }
}
