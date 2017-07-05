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

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import static com.fjordnet.autoreceiver.annotations.ProcessorUtils.*;
import static java.lang.String.format;
import static javax.lang.model.element.ElementKind.CONSTRUCTOR;
import static javax.lang.model.element.ElementKind.FIELD;
import static javax.lang.model.element.ElementKind.METHOD;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.type.TypeKind.VOID;
import static javax.tools.Diagnostic.Kind.ERROR;

/**
 * Generates aspect files for Auto Receiver.
 */
public class AspectGenerator {

    private static final String CONTEXT_QUALIFIED_NAME = "android.content.Context";
    private static final String BROADCAST_RECEIVER_QUALIFIED_NAME
            = "android.content.BroadcastReceiver";
    private static final String INTENT_QUALIFIED_NAME = "android.content.Intent";

    private final Elements elements;
    private final Types types;
    private final Filer filer;
    private final Messager messager;

    private String contextAccessor;

    public AspectGenerator(Elements elements, Types types, Filer filer, Messager messager) {
        this.elements = elements;
        this.types = types;
        this.filer = filer;
        this.messager = messager;
    }

    public void generateAspectFor(Element classElement, List<Element> methodElements)
            throws IOException {

        contextAccessor = null;

        String packageName = getPackageName(classElement, elements);
        String className = classElement.getSimpleName().toString();
        String aspectName = format("%sReceiverAspect", className);

        FileObject aspectFile = filer.createResource(StandardLocation.SOURCE_OUTPUT,
                packageName, format("%s.aj", aspectName));

        Writer writer = aspectFile.openWriter();

        // Package.
        writer.write(format("package %s;\n\n", packageName));

        // Imports.
        writer.write(format("import %1$s;\nimport %2$s;\nimport %3$s;\n"
                + "import android.content.IntentFilter;\n\n",
                BROADCAST_RECEIVER_QUALIFIED_NAME,
                CONTEXT_QUALIFIED_NAME,
                INTENT_QUALIFIED_NAME));

        // Aspect declaration.
        writer.write(format("public aspect %s {\n\n", aspectName));

        // Intermediate class.
        Element parentClass = getParentClass(classElement, types);
        String injectedClassName = format("ReceiverManaged%s",
                null == parentClass ? "" : parentClass.getSimpleName());

        writer.write(format("\tpublic static abstract class %s%s {\n\n", injectedClassName,
                null == parentClass
                        ? ""
                        : format(" extends %s", getFullyQualifiedName(parentClass, elements))));

        // Generate broadcast receiver logic.
        // If any receivers cannot be automatically registered or unregistered
        // in the injected class, they will be returned in the UnprocessedAutoLogic instance.
        UnprocessedAutoLogic unprocessed = generateBroadcastReceiversFor(methodElements,
                classElement, writer, "\t\t");

        // End intermediate class.
        writer.write("\t}\n\n");

        // Declare parents.
        writer.write(format("\tdeclare parents: %s extends %s;\n\n", className, injectedClassName));

        // Generate advice for registration and unregistration.
        generateAdviceFor(unprocessed, writer, "\t");

        // End aspect.
        writer.write("}\n");

        writer.close();
    }

    protected boolean isValidAnnotatedMethod(ExecutableElement methodElement) {

        Set<Modifier> modifiers = methodElement.getModifiers();

        // Verify scope is not private.
        if (modifiers.contains(PRIVATE)) {
            printMethodValidationError(methodElement, "cannot be private");
            return false;
        }

        // Verify method is not static.
        if (modifiers.contains(STATIC)) {
            printMethodValidationError(methodElement, "cannot be static");
            return false;
        }

        // Verify return type is void.
        if (VOID != methodElement.getReturnType().getKind()) {
            printMethodValidationError(methodElement, "should return void");
            return false;
        }

        return true;
    }

    private UnprocessedAutoLogic generateBroadcastReceiversFor(List<Element> methodElements,
            Element classElement,
            Writer writer,
            String tabs) throws IOException {

        Map<String, List<ExecutableElement>> registrationMap = new HashMap<>();
        Map<String, List<ExecutableElement>> unregistrationMap = new HashMap<>();

        for (Element element : methodElements) {
            ExecutableElement methodElement = (ExecutableElement) element;
            String methodName = methodElement.getSimpleName().toString();

            // Skip invalid methods.
            if (!isValidAnnotatedMethod(methodElement)) {
                continue;
            }

            // Broadcast receiver declaration.
            writer.write(format("%1$sBroadcastReceiver %2$s;\n\n", tabs,
                    getReceiverNameFor(methodName)));

            // Empty callback (overridden by target class).

            // Remove invalid modifiers.
            Set<Modifier> modifiers = new HashSet<>(methodElement.getModifiers());
            modifiers.remove(ABSTRACT);
            modifiers.remove(FINAL);

            writer.write(format("%s%s abstract void %s(%s);\n\n",
                    tabs,
                    join(modifiers, ", "),
                    methodName,
                    stringifyParameters(methodElement)));

            // Determine registration and unregistration methods.
            OnReceiveBroadcast annotation = methodElement.getAnnotation(OnReceiveBroadcast.class);

            String registerIn = annotation.registerIn();
            List<ExecutableElement> registrationMethods;
            if (registrationMap.containsKey(registerIn)) {
                registrationMethods = registrationMap.get(registerIn);
            } else {
                registrationMethods = new ArrayList<>();
                registrationMap.put(registerIn, registrationMethods);
            }
            registrationMethods.add(methodElement);

            String unregisterIn = annotation.unregisterIn();
            List<ExecutableElement> unregistrationMethods;
            if (unregistrationMap.containsKey(unregisterIn)) {
                unregistrationMethods = unregistrationMap.get(unregisterIn);
            } else {
                unregistrationMethods = new ArrayList<>();
                unregistrationMap.put(unregisterIn, unregistrationMethods);
            }
            unregistrationMethods.add(methodElement);
        }

        // Registration methods.
        UnprocessedAutoLogic unprocessed = new UnprocessedAutoLogic();
        ExecutableElement unprocessedMethod;
        for (String methodName : registrationMap.keySet()) {
            unprocessedMethod = generateAutoLogicFor(methodName, classElement, writer, tabs,
                    new RegistrationCodeWriter(registrationMap.get(methodName)));
            if (null != unprocessedMethod) {
                unprocessed.registrationMap.put(unprocessedMethod, registrationMap.get(methodName));
            }
        }

        // Unregistration methods.
        for (String methodName : unregistrationMap.keySet()) {
            unprocessedMethod = generateAutoLogicFor(methodName, classElement, writer, tabs,
                    new UnregistrationCodeWriter(unregistrationMap.get(methodName)));
            if (null != unprocessedMethod) {
                unprocessed.unregistrationMap.put(unprocessedMethod,
                        unregistrationMap.get(methodName));
            }
        }

        return unprocessed;
    }

    // Returns unprocessed method or null if it was processed.
    private ExecutableElement generateAutoLogicFor(String methodName,
            Element classElement,
            Writer writer,
            String tabs,
            CodeWriter codeWriter) throws IOException {

        // Find the method.
        ExecutableElement method = findMethodByName(methodName, classElement, types);
        if (null == method) {
            messager.printMessage(ERROR, format("Method %1$s not found: check @%2$s attributes",
                    methodName, OnReceiveBroadcast.class.getSimpleName()));
            return null;
        }

        // If the method is defined within the target class,
        // return as unprocessed, so it can be weaved in via pointcut.
        if (classElement.equals(method.getEnclosingElement())) {
            return method;
        }

        generateOverrideMethodFor(method, classElement, writer, tabs, codeWriter);
        return null;
    }

    private void generateOverrideMethodFor(ExecutableElement method,
            Element classElement,
            Writer writer,
            String tabs,
            CodeWriter codeWriter)
            throws IOException {

        String methodName = method.getSimpleName().toString();

        // Registration method declaration.
        writer.write(format("%1$s@Override\n%1$s%2$s %3$s %4$s(%5$s) {\n%1$s\t",
                tabs,
                join(method.getModifiers(), ", "),
                stringifyReturnType(method),
                methodName,
                stringifyParameters(method)));

        // Check if there's a return type.
        TypeMirror returnType = method.getReturnType();
        boolean hasReturn = VOID != returnType.getKind();
        final String resultVar = "result";
        if (hasReturn) {
            writer.write(format("%1$s %2$s = ", returnType, resultVar));
        }

        // Call super.
        writer.write(
                format("super.%1$s(%2$s);\n\n", methodName, join(method.getParameters(), ", ")));

        // Method implementation.
        codeWriter.writeCode(writer, tabs + "\t",
                getContextAccessorCode(classElement),
                null);

        // Return result, if applicable.
        if (hasReturn) {
            writer.write(format("%1$s\treturn %2$s;\n", tabs, resultVar));
        }

        // End registration method declaration.
        writer.write(format("%1$s}\n\n", tabs));
    }

    private void generateAdviceFor(UnprocessedAutoLogic unprocessed,
            Writer writer,
            String tabs)
            throws IOException {

        // Registration.
        for (ExecutableElement method : unprocessed.registrationMap.keySet()) {
            generateAdviceFor(method, writer, tabs,
                    new RegistrationCodeWriter(unprocessed.registrationMap.get(method)));
        }

        // Unregistration.
        for (ExecutableElement method : unprocessed.unregistrationMap.keySet()) {
            generateAdviceFor(method, writer, tabs,
                    new UnregistrationCodeWriter(unprocessed.unregistrationMap.get(method)));
        }
    }

    private void generateAdviceFor(ExecutableElement method,
            Writer writer,
            String tabs,
            CodeWriter codeWriter) throws IOException {

        String classVar = "targetInstance";
        Element classElement = method.getEnclosingElement();

        // Advice.

        // After with arguments.
        writer.write(format("%1$safter(%2$s %3$s): ", tabs, classElement.asType(), classVar));

        // Pointcut.
        writer.write(format("execution(%1$s %2$s.%3$s(..)) && target(%4$s) {\n\n",
                stringifyReturnType(method), classElement.getSimpleName(), method.getSimpleName(),
                classVar));

        // Advice implementation.
        codeWriter.writeCode(writer, tabs + "\t", getContextAccessorCode(classElement), classVar);

        // End advice.
        writer.write(format("%1$s}\n\n", tabs));
    }

    private String getContextAccessorCode(Element classElement) throws IOException {

        if (null != contextAccessor) {
            return contextAccessor;
        }

        // Find context accessor element, if available.
        contextAccessor = findContextAccessorElement(classElement);

        if (null == contextAccessor) {
            // Access to context is required for
            // automatically registering / unregistering receivers.
            messager.printMessage(ERROR,
                    format("No accessible context available from class %1$s needed for auto "
                            + "registration / unregistration of broadcast receivers specified "
                            + "by methods annotated with @%2$s",
                            classElement,
                            OnReceiveBroadcast.class.getCanonicalName()));
        }

        return contextAccessor;
    }

    private String findContextAccessorElement(Element classElement) {

        TypeMirror contextType = elements.getTypeElement(CONTEXT_QUALIFIED_NAME).asType();

        // If the class itself extends from Context, return it.
        if (types.isAssignable(classElement.asType(), contextType)) {
            return "";
        }

        // Iterate through enclosed members and fields
        // to determine if one of them returns a Context.
        List<? extends Element> memberElements = classElement.getEnclosedElements();
        for (Element member : memberElements) {

            // Skip elements that have private scope.
            if (member.getModifiers().contains(Modifier.PRIVATE)) {
                continue;
            }

            // Do further validation on field candidates.
            if (FIELD == member.getKind() && types.isAssignable(member.asType(), contextType)) {
                return member.getSimpleName().toString();
            }

            // Do further validation on method candidates.
            if (METHOD == member.getKind()) {
                ExecutableElement method = (ExecutableElement) member;

                // Skip methods that don't return a Context,
                // that are constructors,
                // that have parameters,
                // or that throw checked exceptions.
                if (!types.isAssignable(method.getReturnType(), contextType)
                        || CONSTRUCTOR == method.getKind()
                        || !method.getParameters().isEmpty()
                        || throwsCheckedExceptions(method, elements, types)) {

                    continue;
                }

                return format("%1$s()", method.getSimpleName().toString());
            }
        }

        // Nothing found in this class. Search parent class, if available.
        Element parentClass = getParentClass(classElement, types);
        return null == parentClass
                ? null
                : findContextAccessorElement(parentClass);
    }

    private void printMethodValidationError(ExecutableElement methodElement, String errorFragment) {

        messager.printMessage(ERROR,
                format("Callback methods annotated with @%1$s %2$s: %3$s.%4$s",
                        OnReceiveBroadcast.class.getSimpleName(),
                        errorFragment,
                        getFullyQualifiedName(methodElement.getEnclosingElement(), elements),
                        methodElement.getSimpleName().toString()));
    }

    private static String getReceiverNameFor(Element methodElement) {
        return getReceiverNameFor(methodElement.getSimpleName().toString());
    }

    private static String getReceiverNameFor(String methodName) {
        return format("%1$sReceiver", methodName);
    }

    private static String getVariableInvocationPrefix(String variable) {
        return null == variable || 0 >= variable.length() ? "" : variable + ".";
    }

    private static class UnprocessedAutoLogic {

        Map<ExecutableElement, List<ExecutableElement>> registrationMap;
        Map<ExecutableElement, List<ExecutableElement>> unregistrationMap;

        UnprocessedAutoLogic() {
            registrationMap = new HashMap<>();
            unregistrationMap = new HashMap<>();
        }
    }

    private interface CodeWriter {

        void writeCode(Writer writer, String tabs, String contextVar, String classVar)
                throws IOException;
    }

    private class RegistrationCodeWriter implements CodeWriter {

        private List<ExecutableElement> methods;

        private RegistrationCodeWriter(List<ExecutableElement> methods) {
            this.methods = methods;
        }

        @Override
        public void writeCode(Writer writer, String tabs, String contextVar, String classVar)
                throws IOException {

            final TypeMirror intentType = elements.getTypeElement(INTENT_QUALIFIED_NAME).asType();
            final TypeMirror receiverType = elements.getTypeElement(
                    BROADCAST_RECEIVER_QUALIFIED_NAME).asType();

            writer.write(format("%1$sIntentFilter filter;\n\n", tabs));

            for (ExecutableElement method : methods) {
                String methodName = method.getSimpleName().toString();
                String receiverName = getReceiverNameFor(methodName);

                // Arguments for the callback.
                List<String> args = new ArrayList<>();
                for (VariableElement parameter : method.getParameters()) {

                    TypeMirror parameterType = parameter.asType();
                    if (types.isSameType(intentType, parameterType)) {
                        args.add("intent");
                    } else if (types.isSameType(receiverType, parameterType)) {
                        args.add("this");
                    } else {
                        printMethodValidationError(method, format(
                                "cannot have parameter %1$s %2$s",
                                parameterType,
                                parameter.getSimpleName()));
                    }
                }

                String classInvocationPrefix = getVariableInvocationPrefix(classVar);

                // Broadcast receiver definition.
                writer.write(format("%1$s%2$s%3$s = new BroadcastReceiver() {\n", tabs,
                        classInvocationPrefix, receiverName));
                writer.write(format("%1$s@Override\n"
                                + "%1$spublic void onReceive(Context context, Intent intent) {\n"
                                + "%1$s\t%2$s%3$s(%4$s);\n"
                                + "%1$s}\n",

                        tabs + "\t",
                        classInvocationPrefix,
                        methodName,
                        join(args, ", ")));
                writer.write(format("%1$s};\n\n", tabs));

                // Intent filter actions.
                String[] broadcastActions = method.getAnnotation(OnReceiveBroadcast.class).value();
                if (0 >= broadcastActions.length) {
                    printMethodValidationError(method,
                            "must specify at least one broadcast action in its annotation value");
                }

                writer.write(format("%1$sfilter = new IntentFilter();\n", tabs));
                for (String action : broadcastActions) {
                    writer.write(format("%1$sfilter.addAction(\"%2$s\");\n", tabs, action));
                }

                // Register broadcast receiver.
                writer.write(format("%1$s%2$s%3$sregisterReceiver(%2$s%4$s, filter);\n\n",
                        tabs,
                        classInvocationPrefix,
                        getVariableInvocationPrefix(contextVar),
                        receiverName));
            }
        }
    }

    private class UnregistrationCodeWriter implements CodeWriter {

        private List<ExecutableElement> methods;

        private UnregistrationCodeWriter(List<ExecutableElement> methods) {
            this.methods = methods;
        }

        @Override
        public void writeCode(Writer writer, String tabs, String contextVar, String classVar)
                throws IOException {

            for (ExecutableElement method : methods) {
                writer.write(format("%1$s%2$s%3$sunregisterReceiver(%2$s%4$s);\n",
                        tabs,
                        getVariableInvocationPrefix(classVar),
                        getVariableInvocationPrefix(contextVar),
                        getReceiverNameFor(method)));
            }
        }
    }
}
