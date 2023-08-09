/*
 * Copyright 2023 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.navercorp.pinpoint.test.plugin;

import com.navercorp.pinpoint.test.plugin.agent.PluginTestAgentStarter;
import com.navercorp.pinpoint.test.plugin.classloader.PluginAgentClassLoader;
import com.navercorp.pinpoint.test.plugin.classloader.PluginTestClassLoader;
import com.navercorp.pinpoint.test.plugin.util.URLUtils;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class PluginTestInstanceFactory {

    private final PluginTestContext context;

    public PluginTestInstanceFactory(PluginTestContext context) {
        this.context = context;
    }

    public PluginTestInstance create(ClassLoader parentClassLoader, String testId, PluginAgentClassLoader agentClassLoader, List<String> libs, boolean onSystemClassLoader) throws ClassNotFoundException {
        final String id = testId + ":" + (onSystemClassLoader ? "system" : "child") + ":" + context.getJvmVersion();

        final List<File> fileList = new ArrayList<>();
//        for (String classPath : agentLibs) {
//            File file = new File(classPath);
//            fileList.add(file);
//        }
//        final URL[] agentUrls = URLUtils.fileToUrls(fileList);
//        final PluginAgentClassLoader agentClassLoader = new PluginAgentClassLoader(agentUrls, Thread.currentThread().getContextClassLoader());

        Thread thread = Thread.currentThread();
        ClassLoader currentClassLoader = thread.getContextClassLoader();
        try {
//            thread.setContextClassLoader(agentClassLoader);

            PluginTestInstanceCallback instanceContext = startAgent(context.getConfigFile(), agentClassLoader);
            fileList.clear();
            for (String classPath : getClassPath(libs, onSystemClassLoader)) {
                File file = new File(classPath);
                fileList.add(file);
            }
            final URL[] urls = URLUtils.fileToUrls(fileList);

            PluginTestClassLoader testClassLoader = new PluginTestClassLoader(urls, parentClassLoader, instanceContext);
            testClassLoader.setAgentClassLoader(agentClassLoader);
            agentClassLoader.setTestClassLoader(testClassLoader);

            final Class<?> testClass = testClassLoader.loadClass(context.getTestClass().getName());
            return new DefaultPluginTestInstance(id, agentClassLoader, testClassLoader, testClass, context.isManageTraceObject(), instanceContext);
        } finally {
            thread.setContextClassLoader(currentClassLoader);
        }
    }

    List<String> getClassPath(List<String> libs, boolean onSystemClassLoader) {
        final List<String> libList = new ArrayList<>(context.getRequiredLibraries());
        libList.addAll(libs);
        libList.add(context.getTestClassLocation());
        return libList;
    }

    PluginTestInstanceCallback startAgent(String configFile, ClassLoader classLoader) {
        try {
            Class<?> testClass = classLoader.loadClass(PluginTestAgentStarter.class.getName());
            Constructor<?> constructor = testClass.getConstructor(String.class, ClassLoader.class);
            Method method = testClass.getDeclaredMethod("getCallback");
            return (PluginTestInstanceCallback) method.invoke(constructor.newInstance(configFile, classLoader));
        } catch (Exception e) {
            throw new RuntimeException("agent configFile=" + configFile + ", classLoader=" + classLoader, e);
        }
    }
}
