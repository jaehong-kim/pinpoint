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

package com.navercorp.pinpoint.test.plugin.classloader;

import com.navercorp.pinpoint.common.util.IOUtils;
import com.navercorp.pinpoint.profiler.util.JavaAssistUtils;
import com.navercorp.pinpoint.test.plugin.TranslatorAdaptor;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.util.Arrays;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class PluginTestClassLoader extends PluginClassLoader {
    public static final IsPinpointPackage isPinpointPackage = new IsPinpointPackage();
    public static final IsPinpointBootstrapPluginTestPackage isPinpointBootstrapPluginTestPackage = new IsPinpointBootstrapPluginTestPackage();

    private PluginAgentClassLoader agentClassLoader;
    private TranslatorAdaptor translator;

    public PluginTestClassLoader(URL[] urls, ClassLoader parent, TranslatorAdaptor translator) {
        super(urls, parent);
        this.translator = translator;
        setClassLoaderName(getClass().getSimpleName());
        System.out.println(getClassLoaderName() + Arrays.asList(urls));
    }

    public void setAgentClassLoader(PluginAgentClassLoader agentClassLoader) {
        this.agentClassLoader = agentClassLoader;
    }

    @Override
    protected boolean isDelegated(String name) {
        return super.isDelegated(name) || isPinpointBootstrapPluginTestPackage.test(name);
    }

    @Override
    public Class<?> loadClassChildFirst(String name) throws ClassNotFoundException {
        if (isPinpointPackage.test(name)) {
            if (agentClassLoader != null) {
                return agentClassLoader.loadClass(name, false);
            }
        }

        if(name.equals("org.elasticsearch.client.indexlifecycle.IndexLifecycleNamedXContentProvider")) {
            System.out.println("FIND");
        }

        final String classInternalName = JavaAssistUtils.javaClassNameToJvmResourceName(name);
        final URL url = getResource(classInternalName);
        if (url != null) {
            try {
                byte[] classfile = translator.transform(this, name, IOUtils.toByteArray(url.openStream()));
                if (classfile != null) {
                    CodeSigner[] signers = null;
                    URLConnection urlConnection = url.openConnection();
                    if(urlConnection instanceof JarURLConnection) {
                        JarFile jarFile = ((JarURLConnection) urlConnection).getJarFile();
                        JarEntry entry = jarFile.getJarEntry(classInternalName);
                        signers = entry.getCodeSigners();
                    }
                    CodeSource cs = new CodeSource(url, signers);

                    return defineClass(name, classfile, 0, classfile.length);
                }
            } catch (IOException e) {
            }
        }

        return findClass(name);
    }


    @Override
    protected boolean isChild(String name) {
        return false;
    }

    public boolean isLoadedClass(String name) {
        return findLoadedClass(name) != null;
    }

    @Override
    public URL getResource(String name) {
        System.out.println(getClassLoaderName() + "-getResource=" + name);
        final String className = JavaAssistUtils.jvmNameToJavaName(name);
        if (isDelegated(className)) {
            System.out.println(getClassLoaderName() + "-super.getResource=" + name);
            return super.getResource(name);
        }

        URL url = findResource(name);
        if (url != null) {
            System.out.println(getClassLoaderName() + "-findResource=" + name);
            return url;
        }
        return null;
    }
}
