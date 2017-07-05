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

import com.google.auto.service.AutoService;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static javax.lang.model.element.ElementKind.CLASS;
import static javax.tools.Diagnostic.Kind.ERROR;

@AutoService(Processor.class)
public class AutoReceiverProcessor extends AbstractProcessor {

    private Elements elementUtils;
    private Types typeUtils;
    private Filer filer;
    private Messager messager;

    @Override
    public synchronized void init(ProcessingEnvironment environment) {
        super.init(environment);

        elementUtils = environment.getElementUtils();
        typeUtils = environment.getTypeUtils();
        filer = environment.getFiler();
        messager = environment.getMessager();
    }

    @Override
    public boolean process(Set<? extends TypeElement> elements, RoundEnvironment roundEnv) {

        Map<Element, List<Element>> classMethodMap = new HashMap<>();
        List<Element> methods;
        Element parent;

        for (Element method : roundEnv.getElementsAnnotatedWith(OnReceiveBroadcast.class)) {

            parent = method.getEnclosingElement();
            if (!CLASS.equals(parent.getKind())) {
                continue;
            }

            methods = classMethodMap.get(parent);
            if (null == methods) {
                methods = new ArrayList<>();
            }
            methods.add(method);
            classMethodMap.put(parent, methods);
        }

        AspectGenerator aspectGenerator = new AspectGenerator(elementUtils, typeUtils, filer,
                messager);

        for (Element classElement : classMethodMap.keySet()) {

            try {
                aspectGenerator.generateAspectFor(classElement, classMethodMap.get(classElement));

            } catch (Exception exception) {

                messager.printMessage(ERROR,
                        String.format("Unable to write aspect for %s due to exception: %s\n%s",
                                classElement.getSimpleName(), exception, getStackTrace(exception)),
                        classElement);
            }
        }

        return true;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> annotations = new LinkedHashSet<>();
        annotations.add(OnReceiveBroadcast.class.getCanonicalName());
        return annotations;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    private String getStackTrace(Exception exception) {
        StringWriter stringWriter = new StringWriter();
        exception.printStackTrace(new PrintWriter(stringWriter, true));
        return stringWriter.getBuffer().toString();
    }
}
