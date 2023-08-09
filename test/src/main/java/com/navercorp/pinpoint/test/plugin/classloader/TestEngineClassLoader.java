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

import com.navercorp.pinpoint.common.util.JvmUtils;
import com.navercorp.pinpoint.common.util.JvmVersion;
import com.navercorp.pinpoint.profiler.util.JavaAssistUtils;

import java.net.URL;
import java.net.URLClassLoader;

public class TestEngineClassLoader extends URLClassLoader {
    // find child first classloader
    static {
        registerAsParallelCapable();
    }

    public TestEngineClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    protected Class<?> loadClass(String name, boolean resolve)
            throws ClassFormatError, ClassNotFoundException {

        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            System.out.println("findLoadedClass=" + c);
            if (c == null) {
                c = loadClassByDelegation(name);
                System.out.println("loadClassByDelegation=" + c);
            }

            if (c == null) {
                try {
                    c = findClass(name);
                } catch (ClassNotFoundException ignored) {
                }
                System.out.println("findClass=" + c);
            }

            if (c == null) {
                c = delegateToParent(name);
                System.out.println("delegateToParent=" + c);
            }

            if (resolve) {
                resolveClass(c);
            }

            return c;
        }
    }

    protected Class<?> loadClassByDelegation(String name)
            throws ClassNotFoundException {
        /* The swing components must be loaded by a system
         * class loader.
         * javax.swing.UIManager loads a (concrete) subclass
         * of LookAndFeel by a system class loader and cast
         * an instance of the class to LookAndFeel for
         * (maybe) a security reason.  To avoid failure of
         * type conversion, LookAndFeel must not be loaded
         * by this class loader.
         */

        Class<?> c = null;
        if (isJdkPackage(name) || isDelegated(name))
            c = delegateToParent(name);

        return c;
    }

    private boolean isJdkPackage(String name) {
        final JvmVersion version = JvmUtils.getVersion();
        if (version.onOrAfter(JvmVersion.JAVA_9)) {
            if (name.startsWith("javax.xml.bind")
                    || name.startsWith("javax.annotation")) {
                return false;
            }
        }

        if (name.startsWith("javax.jms")
                || name.startsWith("javax.ws")) {
            return false;
        }
        return name.startsWith("java.")
                || name.startsWith("jdk.")
                || name.startsWith("javax.")
                || name.startsWith("sun.")
                || name.startsWith("com.sun.")
                || name.startsWith("org.w3c.")
                || name.startsWith("org.xml.");
    }

    private boolean isDelegated(String name) {
        return name.startsWith("com.navercorp.pinpoint.test.")
                || name.startsWith("org.apache.logging.")
                || name.startsWith("org.slf4j.")
                || name.startsWith("com.fasterxml.");
    }

    protected Class<?> delegateToParent(String classname)
            throws ClassNotFoundException {
        ClassLoader cl = getParent();
        if (cl != null) {
            return cl.loadClass(classname);
        } else {
            return findSystemClass(classname);
        }
    }

    boolean isLogPackage(String name) {
        return name.startsWith("org.slf4j");
    }

    @Override
    public URL getResource(String name) {
        final String className = JavaAssistUtils.jvmNameToJavaName(name);
        if (isJdkPackage(className) || isDelegated(className)) {
            return super.getResource(name);
        }

        final URL url = findResource(name);
        if (url != null) {
            return url;
        }
        return super.getResource(name);
    }
}
