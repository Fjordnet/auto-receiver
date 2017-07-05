/*
 * Copyright 2017 FJORD
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.fjordnet.autoreceiver.annotations;

import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static java.lang.String.format;
import static javax.lang.model.element.ElementKind.METHOD;
import static javax.lang.model.type.TypeKind.DECLARED;

/**
 * Utility methods for annotation processing.
 */
public abstract class ProcessorUtils {

    protected ProcessorUtils() {
    }

    /**
     * Query whether the target element is annotated with the specified annotation.
     *
     * @param element the element whose annotations will be checked.
     * @param simpleAnnotationName the simple name of the annotation to query against the
     * annotations on the specified element.
     *
     * @return {@code true} if the element is annotated with the specified annotation name.
     */
    public static boolean isElementAnnotatedWith(final Element element,
            final String simpleAnnotationName) {

        List<? extends AnnotationMirror> annotationMirrors = element.getAnnotationMirrors();
        String annotationName;

        for (AnnotationMirror annotationMirror : annotationMirrors) {
            annotationName = annotationMirror.getAnnotationType()
                    .asElement()
                    .getSimpleName()
                    .toString();

            if (simpleAnnotationName.equals(annotationName)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get the package name of the element.
     *
     * @param element the element whose package name is to be queried.
     * @param elementUtils {@link Elements} instance from
     * {@link javax.annotation.processing.ProcessingEnvironment}.
     *
     * @return the package name of the element.
     */
    public static String getPackageName(Element element, Elements elementUtils) {
        return elementUtils.getPackageOf(element).getQualifiedName().toString();
    }

    /**
     * Get the fully qualified name of the element.
     *
     * @param element the element fully qualified name is to be queried.
     * @param elementUtils {@link Elements} instance from
     * {@link javax.annotation.processing.ProcessingEnvironment}.
     *
     * @return the fully qualified name of the element.
     */
    public static String getFullyQualifiedName(Element element, Elements elementUtils) {
        return String.format("%s.%s", getPackageName(element, elementUtils),
                element.getSimpleName());
    }

    /**
     * Get the parent class of the specified class element.
     *
     * @param classElement the class element whose parent element is to be retrieved.
     * @param typeUtils {@link Types} instance from
     * {@link javax.annotation.processing.ProcessingEnvironment}.
     *
     * @return the parent class of the specified class element.
     */
    public static Element getParentClass(Element classElement,
            Types typeUtils) {

        return null == classElement || DECLARED != classElement.asType().getKind()
                ? null
                : typeUtils.asElement(((TypeElement) classElement).getSuperclass());
    }

    /**
     * Retrieve the {@link ExecutableElement} representing the method whose name matches
     * the specified name. This implementation searches through declared methods
     * as well as inherited methods from parent classes for potential matches.
     * An error is thrown if more than one method matches the specified name.
     *
     * @param methodName the name of the method to find.
     * @param classElement the element representing the class which is expected to contain the
     * target method.
     * @param typeUtils {@link Types} instance from
     * {@link javax.annotation.processing.ProcessingEnvironment}.
     *
     * @return the {@link ExecutableElement} representing the method whose name matches
     * the specified name, or {@code null} if none are found, or more than 1 is found.
     * @throws IllegalArgumentException if there was more than 1 matching method found.
     */
    public static ExecutableElement findMethodByName(
            String methodName,
            Element classElement,
            Types typeUtils)
            throws IllegalArgumentException {

        List<? extends Element> elements = classElement.getEnclosedElements();
        boolean isDeclared = false;
        ExecutableElement methodElement = null;

        for (Element element : elements) {

            if (METHOD != element.getKind()
                    || !methodName.equals(element.getSimpleName().toString())) {

                continue;
            }

            // If more than one method was found, raise an error.
            if (isDeclared) {
                throw new IllegalArgumentException(
                        format("Multiple methods with name %s found", methodName));
            }

            isDeclared = true;
            methodElement = (ExecutableElement) element;
        }

        if (null != methodElement) {
            return methodElement;
        }

        // If the method wasn't found, look for it in parent classes.
        Element parentElement = getParentClass(classElement, typeUtils);
        return null == parentElement
                ? null
                : findMethodByName(methodName, parentElement, typeUtils);
    }

    /**
     * Query whether the specified method element throws any checked exceptions.
     *
     * @param methodElement the method element in question.
     * @param elementUtils {@link Elements} instance from
     * {@link javax.annotation.processing.ProcessingEnvironment}.
     * @param typeUtils {@link Types} instance from
     * {@link javax.annotation.processing.ProcessingEnvironment}.
     *
     * @return {@code true} if the specified method throws at least one checked exception.
     */
    public static boolean throwsCheckedExceptions(ExecutableElement methodElement,
            Elements elementUtils,
            Types typeUtils) {

        // Checked exceptions are all exceptions that aren't subtypes of Error or RuntimeException.

        TypeMirror runtimeExceptionType = elementUtils.getTypeElement("java.lang.RuntimeException")
                .asType();
        TypeMirror errorType = elementUtils.getTypeElement("java.lang.Error").asType();

        for (TypeMirror thrownType : methodElement.getThrownTypes()) {

            if (!typeUtils.isAssignable(thrownType, runtimeExceptionType)
                    && !typeUtils.isAssignable(thrownType, errorType)) {

                return true;
            }
        }

        return false;
    }

    /**
     * Build a string representing all items provided by the specified {@link Iterable},
     * separated by the specified delimiter.
     *
     * @param iterable the {@link Iterable} containing the items from which to build
     * the joined string.
     * @param delimiter the sequence of characters to separate each item in the {@link Iterable}.
     * @return a string representing all items provided by the {@link Iterable},
     * separated by the specified delimiter.
     * @param <ItemType> the type of the items in the {@link Iterable}.
     *
     * @return a string representing all items provided by the {@link Iterable},
     * separated by the specified delimiter.
     */
    public static <ItemType> String join(Iterable<ItemType> iterable,
            String delimiter) {

        return join(iterable, delimiter, Object::toString);
    }

    /**
     * Build a string representing all items provided by the specified {@link Iterable},
     * separated by the specified delimiter.
     *
     * @param iterable the {@link Iterable} containing the items from which to build
     * the joined string.
     * @param delimiter the sequence of characters to separate each item in the {@link Iterable}.
     * @param serializer optional function for converting items into a string representation.
     * @param <ItemType> the type of the items in the {@link Iterable}.
     *
     * @return a string representing all items provided by the {@link Iterable},
     * separated by the specified delimiter.
     */
    public static <ItemType> String join(Iterable<ItemType> iterable,
            String delimiter,
            ItemSerializer<ItemType> serializer) {

        if (null == iterable) {
            return "";
        }

        if (null == delimiter) {
            delimiter = "";
        }

        StringBuilder builder = new StringBuilder();
        for (ItemType item : iterable) {
            builder.append(delimiter).append(serializer.serialize(item));
        }

        return delimiter.length() > builder.length()
                ? builder.toString()
                : builder.substring(delimiter.length());
    }

    public interface ItemSerializer<ItemType> {
        String serialize(ItemType item);

    }

    /**
     * Obtain the string representation of the specified method's return type.
     *
     * @param methodElement the method whose return type is to be retrieved as a string.
     *
     * @return the string representation of the specified method's return type.
     */
    public static String stringifyReturnType(ExecutableElement methodElement) {
        return methodElement.getReturnType().toString();
    }

    /**
     * Obtain the string representation of the specified method's parameter list.
     *
     * @param methodElement the method whose parameters are to be retrieved as a string.
     *
     * @return the string representation of the specified method's parameter list.
     */
    public static String stringifyParameters(ExecutableElement methodElement) {

        return join(methodElement.getParameters(), ", ",
                parameter -> format("%s %s", parameter.asType(), parameter.getSimpleName()));
    }
}
