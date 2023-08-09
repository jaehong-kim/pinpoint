/*
 * Copyright 2018 NAVER Corp.
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

package com.navercorp.pinpoint.test.plugin.classloader;

import java.net.URL;
import java.util.Arrays;

public class PluginAgentClassLoader extends PluginClassLoader {
    public static final IsPinpointPackage isPinpointPackage = new IsPinpointPackage();
    public static final IsPinpointTestPackage isPinpointTestPackage = new IsPinpointTestPackage();
    public static final IsPinpointTestAgentPackage isPinpointTestAgentPackage = new IsPinpointTestAgentPackage();
    public static final IsPinpointBootstrapPluginTestPackage isPinpointBootstrapPluginTestPackage = new IsPinpointBootstrapPluginTestPackage();

    private PluginTestClassLoader testClassLoader;

    public PluginAgentClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
        setClassLoaderName(getClass().getSimpleName());
        System.out.println(getClassLoaderName() + Arrays.asList(urls));
    }

    public void setTestClassLoader(PluginTestClassLoader testClassLoader) {
        this.testClassLoader = testClassLoader;
    }

    @Override
    public boolean isDelegated(String name) {
        if (isPinpointTestAgentPackage.test(name)) {
            return false;
        }
        return super.isDelegated(name) || isPinpointTestPackage.test(name) || isPinpointBootstrapPluginTestPackage.test(name);
    }

    @Override
    public Class<?> loadClassChildFirst(String name) throws ClassNotFoundException {
        // Find provided class
        if (testClassLoader != null && Boolean.FALSE == isPinpointPackage.test(name)) {
            if (testClassLoader.isLoadedClass(name)) {
                return testClassLoader.loadClass(name, false);
            }
        }

        Class<?> c = null;
        try {
            c = findClass(name);
        } catch (ClassNotFoundException ignored) {
        }

        if (c == null) {
            if (testClassLoader != null) {
                c = testClassLoader.loadClass(name, false);
            }
        }
        return c;
    }
}
