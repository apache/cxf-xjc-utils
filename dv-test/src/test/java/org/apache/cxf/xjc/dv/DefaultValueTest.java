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

import java.io.StringWriter;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import jakarta.xml.bind.DatatypeConverter;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.adapters.HexBinaryAdapter;
import org.apache.cxf.configuration.foo.Foo;

import org.junit.Assert;
import org.junit.Test;



public class DefaultValueTest extends Assert {

    @Test
    public void testFooDefaultValues() throws Exception {

        Foo foo = new org.apache.cxf.configuration.foo.ObjectFactory().createFoo();

        // verify default values
        checkCXF3131(foo);

        assertAttributeValuesWithoutDefault(foo);
        assertDefaultAttributeValues(foo);        
        assertDefaultElementValues(foo);
        assertSchemaValid(foo);
    }

    private void checkCXF3131(Foo foo) throws Exception {
        Field f = foo.getClass().getDeclaredField("point");
        assertNotNull(f.getAnnotation(XmlElement.class));
    }

    private void assertDefaultAttributeValues(Foo foo) {
        assertEquals("Unexpected value for attribute stringAttr",
                     "hello", foo.getStringAttr());
        assertTrue("Unexpected value for attribute booleanAttr",
                     foo.isBooleanAttr());
        assertEquals("Unexpected value for attribute integerAttr",
                     new BigInteger("111"), foo.getIntegerAttr());
        assertEquals("Unexpected value for attribute intAttr",
                     112, foo.getIntAttr());
        assertEquals("Unexpected value for attribute longAttr",
                     113L, foo.getLongAttr());
        assertEquals("Unexpected value for attribute shortAttr",
                     114, foo.getShortAttr());
        assertEquals("Unexpected value for attribute decimalAttr",
                     new BigDecimal("115"), foo.getDecimalAttr());
        assertEquals("Unexpected value for attribute floatAttr",
                     116F, foo.getFloatAttr(), 0F);
        assertEquals("Unexpected value for attribute doubleAttr",
                     117D, foo.getDoubleAttr(), 0D);
        assertEquals("Unexpected value for attribute byteAttr",
                     118, foo.getByteAttr());
        
        byte[] expected = DatatypeConverter.parseBase64Binary("wxyz");
        byte[] effective = foo.getBase64BinaryAttr();
        
        assertEquals("Unexpected value for attribute base64BinaryAttr", expected.length, effective.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals("Unexpected value for attribute base64BinaryAttr", expected[i], effective[i]);
        }
        
        expected = new HexBinaryAdapter().unmarshal("aaaa");
        effective = foo.getHexBinaryAttr();
        assertEquals("Unexpected value for attribute hexBinaryAttr", expected.length, effective.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals("Unexpected value for attribute hexBinaryAttr", expected[i], effective[i]);
        }
                
        QName qn = foo.getQnameAttr();
        assertEquals("Unexpected value for attribute qnameAttr",
                     "http://www.w3.org/2001/XMLSchema", qn.getNamespaceURI());
        assertEquals("Unexpected value for attribute qnameAttr",
                     "schema", qn.getLocalPart());
       
        assertEquals("Unexpected value for attribute unsignedIntAttr",
                     119L, foo.getUnsignedIntAttr());
        assertEquals("Unexpected value for attribute unsignedShortAttr",
                     120, foo.getUnsignedShortAttr());
        assertEquals("Unexpected value for attribute unsignedByteAttr",
                     121, foo.getUnsignedByteAttr());

        assertEquals("Unexpected value for attribute durationAttr",
                     3, foo.getDurationAttr().getSeconds());
        assertEquals("Unexpected value for attribute durationAttr",
                     0, foo.getDurationAttr().getHours());
    }
    
    /**
     * @param foo
     */
    private void assertAttributeValuesWithoutDefault(Foo foo) {
        assertNull("Unexpected value for attribute stringAttrNoDefault",
                     foo.getStringAttrNoDefault());
        assertNull("Unexpected value for attribute booleanAttrNoDefault",
                     foo.isBooleanAttrNoDefault());
        assertNull("Unexpected value for attribute integerAttrNoDefault",
                     foo.getIntegerAttrNoDefault());
        assertNull("Unexpected value for attribute intAttrNoDefault",
                     foo.getIntAttrNoDefault());
        assertNull("Unexpected value for attribute longAttrNoDefault",
                     foo.getLongAttrNoDefault());
        assertNull("Unexpected value for attribute shortAttrNoDefault",
                     foo.getShortAttrNoDefault());
        assertNull("Unexpected value for attribute decimalAttrNoDefault",
                     foo.getDecimalAttrNoDefault());
        assertNull("Unexpected value for attribute floatAttrNoDefault",
                     foo.getFloatAttrNoDefault());
        assertNull("Unexpected value for attribute doubleAttrNoDefault",
                     foo.getDoubleAttrNoDefault());
        assertNull("Unexpected value for attribute byteAttrNoDefault",
                     foo.getByteAttrNoDefault());
        
        assertNull("Unexpected value for attribute base64BinaryAttrNoDefault",
                   foo.getBase64BinaryAttrNoDefault());
        assertNull("Unexpected value for attribute hexBinaryAttrNoDefault",
                   foo.getHexBinaryAttrNoDefault());
        
        assertNull("Unexpected value for attribute qnameAttrNoDefault",
                     foo.getQnameAttrNoDefault());
       
        assertNull("Unexpected value for attribute unsignedIntAttrNoDefault",
                     foo.getUnsignedIntAttrNoDefault());
        assertNull("Unexpected value for attribute unsignedShortAttrNoDefault",
                     foo.getUnsignedShortAttrNoDefault());
        assertNull("Unexpected value for attribute unsignedByteAttrNoDefault",
                     foo.getUnsignedByteAttrNoDefault());
        assertNull("Unexpected value for attribute durationAttrNoDefault",
                     foo.getDurationAttrNoDefault());
    }
    
    private void assertDefaultElementValues(Foo foo) {
        assertNull(foo.getPageColor());
        assertEquals("Unexpected value for element pageColor.background", "red", 
                     foo.getPageColor2().getBackground());
        assertEquals("Unexpected value for element pageColor.foreground", "blue", 
                     foo.getPageColor2().getForeground());

        assertEquals("Unexpected value for element driving",
                     "LeftTurn", foo.getDriving().value());
        assertEquals("Unexpected value for element stringElem",
                     "hello", foo.getStringElem());
        assertTrue("Unexpected value for element booleanElem",
                     foo.isBooleanElem());
        assertEquals("Unexpected value for element integerElem",
                     new BigInteger("11"), foo.getIntegerElem());
        assertEquals("Unexpected value for element intElem",
                     12, foo.getIntElem().intValue());
        assertEquals("Unexpected value for element longElem",
                     13L, foo.getLongElem().longValue());
        assertEquals("Unexpected value for element shortElem",
                     (short)14, foo.getShortElem().shortValue());
        assertEquals("Unexpected value for element decimalElem",
                     new BigDecimal("15"), foo.getDecimalElem());
        assertEquals("Unexpected value for element floatElem",
                     16F, foo.getFloatElem(), 0F);
        assertEquals("Unexpected value for element doubleElem",
                     17D, foo.getDoubleElem(), 0D);
        assertEquals("Unexpected value for element byteElem",
                     (byte)18, foo.getByteElem().byteValue());
        
        byte[] expected = DatatypeConverter.parseBase64Binary("abcdefgh");
        byte[] effective = foo.getBase64BinaryElem();
        
        assertEquals("Unexpected value for element base64BinaryElem", expected.length, effective.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals("Unexpected value for element base64BinaryElem", expected[i], effective[i]);
        }
        
        expected = new HexBinaryAdapter().unmarshal("ffff");
        effective = foo.getHexBinaryElem();
        assertEquals("Unexpected value for element hexBinaryElem", expected.length, effective.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals("Unexpected value for element hexBinaryElem", expected[i], effective[i]);
        }
                
        QName qn = foo.getQnameElem();
        assertEquals("Unexpected value for element qnameElem",
                     "http://www.w3.org/2001/XMLSchema", qn.getNamespaceURI());
        assertEquals("Unexpected value for element qnameElem",
                     "string", qn.getLocalPart());
       
        assertEquals("Unexpected value for element unsignedIntElem",
                     19L, foo.getUnsignedIntElem().longValue());
        assertEquals("Unexpected value for element unsignedShortElem",
                     20, foo.getUnsignedShortElem().intValue());
        assertEquals("Unexpected value for element unsignedByteElem",
                     (short)21, foo.getUnsignedByteElem().shortValue());

        assertEquals("Unexpected value for element durationElem",
                     0, foo.getDurationElem().getSeconds());
        assertEquals("Unexpected value for element durationElem",
                     3, foo.getDurationElem().getHours());
    }
    
    private void assertSchemaValid(Foo foo) throws Exception {
        Marshaller marshaller = JAXBContext.newInstance(Foo.class).createMarshaller();
        SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema = sf.newSchema(getClass().getResource("/schemas/configuration/foo.xsd"));
        marshaller.setSchema(schema);
        
        JAXBElement<Foo> fooElement = 
                new org.apache.cxf.configuration.foo.ObjectFactory().createFooElement(foo);
        
        marshaller.marshal(fooElement, new StringWriter());
    }
}
