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

import java.util.List;

import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.TagElement;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

/**
 * Utility methods for tests
 * 
 * @author Dawid Pytel
 */
public final class JavadocTestHelper {
    private JavadocTestHelper() {
        //utility class
    }

    public static Matcher<Javadoc> javadocContains(final String comment) {
        return new TypeSafeMatcher<Javadoc>(Javadoc.class) {

            @Override
            protected boolean matchesSafely(Javadoc javadoc) {
                if (!javadoc.tags().isEmpty()) {
                    TagElement tagElement = (TagElement)javadoc.tags().get(0);
                    List<?> fragments = tagElement.fragments();
                    for (Object fragment : fragments) {
                        if (fragment != null && fragment.toString().contains(comment)) {
                            return true;
                        }
                    }
                }
                return false;
            }

            public void describeTo(Description description) {
                description.appendText("javadoc contains given comment: " + comment);
            }
        };
    }

    public static Matcher<Javadoc> containsTag(final String tagName, final String tagValue) {
        return new TypeSafeMatcher<Javadoc>(Javadoc.class) {

            @Override
            protected boolean matchesSafely(Javadoc javadoc) {
                @SuppressWarnings("unchecked")
                List<TagElement> tags = javadoc.tags();
                for (TagElement tagElement : tags) {
                    if (tagName.equals(tagElement.getTagName())) {
                        return tagValue.equals(tagElement.fragments().get(0).toString());
                    }
                }

                return false;
            }

            public void describeTo(Description description) {
                description.appendText("javadoc contains tag " + tagName + " " + tagValue);
            }
        };
    }
}
