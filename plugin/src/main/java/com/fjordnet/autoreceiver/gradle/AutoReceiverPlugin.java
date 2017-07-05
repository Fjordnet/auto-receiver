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

package com.fjordnet.autoreceiver.gradle;

import com.android.build.gradle.AppExtension;
import com.android.build.gradle.AppPlugin;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.LibraryExtension;
import com.android.build.gradle.LibraryPlugin;
import com.android.build.gradle.api.AndroidSourceSet;
import com.android.build.gradle.api.BaseVariant;

import org.aspectj.bridge.IMessage;
import org.aspectj.bridge.MessageHandler;
import org.aspectj.tools.ajc.Main;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.tasks.compile.AbstractCompile;

import java.io.File;
import java.util.Iterator;

import static org.aspectj.bridge.IMessage.DEBUG;
import static org.aspectj.bridge.IMessage.INFO;
import static org.aspectj.bridge.IMessage.WARNING;

/**
 * Gradle plugin for AutoReceiver.
 * It adds all compile and apt dependencies.
 * It also runs ajc after Java compilation to weave aspects into the compiled code.
 */
public class AutoReceiverPlugin implements Plugin<Project> {

    private static final String LIBRARY_VERSION = "1.0.0";
    private static final String ASPECTJ_RUNTIME_VERSION = "1.8.9";

    private static final String APT = "annotationProcessor";
    private static final String COMPILE = "compile";

    @Override
    public void apply(final Project project) {

        // Verify the project is an Android app or library.
        PluginContainer plugins = project.getPlugins();
        boolean hasAppPlugin = null != plugins.withType(AppPlugin.class);
        boolean hasLibPlugin = null != plugins.withType(LibraryPlugin.class);
        if (!hasAppPlugin && !hasLibPlugin) {
            throw new IllegalStateException("Android app or library plugin is required.");
        }

        // Add project repositories.
        RepositoryHandler repositories = project.getRepositories();
        repositories.jcenter();
        repositories.mavenLocal();

        // Add project dependencies.
        String libDependencyFormat = "com.fjordnet.autoreceiver:%s:" + LIBRARY_VERSION;
        DependencyHandler dependencies = project.getDependencies();
        dependencies.add(APT, String.format(libDependencyFormat, "annotation-processor"));
        dependencies.add(COMPILE, String.format(libDependencyFormat, "annotations"));
        dependencies.add(COMPILE,
                String.format("org.aspectj:aspectjrt:%s", ASPECTJ_RUNTIME_VERSION));

        // Find variants.
        DomainObjectSet<? extends BaseVariant> variants;
        final BaseExtension android = (BaseExtension) project.getExtensions().getByName("android");

        if (hasAppPlugin) {
            variants = ((AppExtension) android).getApplicationVariants();
        } else {
            variants = ((LibraryExtension) android).getLibraryVariants();
        }

        // Execute ajc (aspect weaving) following Java compilation.
        variants.all(new Action<BaseVariant>() {

            @Override
            public void execute(BaseVariant variant) {
                AjcAction ajcAction = new AjcAction(variant.getJavaCompile(), android,
                        project.getLogger());

                variant.getJavaCompiler().doLast(ajcAction);
            }
        });
    }

    private static <ItemType> String join(Iterable<ItemType> iterable) {

        Iterator<ItemType> iter = iterable.iterator();
        StringBuilder buffer = new StringBuilder();

        if (iter.hasNext()) {
            buffer.append(iter.next());
            while (iter.hasNext()) {
                buffer.append(File.pathSeparator).append(iter.next());
            }
        }

        return buffer.toString();
    }

    private static class AjcAction implements Action<Task> {

        private AbstractCompile compiler;
        private BaseExtension android;
        private Logger logger;

        public AjcAction(AbstractCompile compiler, BaseExtension android, Logger logger) {
            this.compiler = compiler;
            this.android = android;
            this.logger = logger;
        }

        @Override
        public void execute(Task task) {
            AndroidSourceSet mainSourceSet = android.getSourceSets().findByName("main");
            String sourceDirs = join(mainSourceSet.getJava().getSrcDirs());

            String buildDir = task.getProject().getBuildDir().toString();

            String destinationDir = compiler.getDestinationDir().toString();
            String inpath = destinationDir;
            String classpath = compiler.getClasspath().getAsPath();
            String bootClasspath = join(android.getBootClasspath());

            String[] args = {
                    "-showWeaveInfo",
                    "-verbose",
                    "-" + android.getCompileOptions().getSourceCompatibility(),
                    "-sourceroots", sourceDirs + File.pathSeparator + buildDir,
                    "-inpath", inpath,
                    "-aspectpath", classpath,
                    "-d", destinationDir,
                    "-classpath", classpath,
                    "-bootclasspath", bootClasspath
            };

            MessageHandler handler = new MessageHandler(true);

            new Main().run(args, handler);

            for (IMessage message : handler.getMessages(null, true)) {
                IMessage.Kind kind = message.getKind();
                if (WARNING.equals(kind)) {
                    logger.warn(message.getMessage(), message.getThrown());
                } else if (INFO.equals(kind)) {
                    logger.info(message.getMessage(), message.getThrown());
                } else if (DEBUG.equals(kind)) {
                    logger.debug(message.getMessage(), message.getThrown());
                } else {
                    logger.error(message.getMessage(), message.getThrown());
                }
            }
        }
    }
}
