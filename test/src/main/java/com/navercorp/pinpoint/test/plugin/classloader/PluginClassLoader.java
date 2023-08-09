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

import com.navercorp.pinpoint.profiler.util.JavaAssistUtils;

import java.net.URL;
import java.net.URLClassLoader;

public class PluginClassLoader extends URLClassLoader {
    public static final IsJdkPackage isJdkPackage = new IsJdkPackage();
    public static final IsLogPackage isLogPackage = new IsLogPackage();
    public static final IsFastXmlPackage isFastXmlPackage = new IsFastXmlPackage();
    public static final IsPinpointTestPackage isPinpointTestPackage = new IsPinpointTestPackage();

    public static final IsJunitPackage isJunitPackage = new IsJunitPackage();

    // find child first classloader
    static {
        registerAsParallelCapable();
    }

    private String classLoaderName;

    public PluginClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
        setClassLoaderName(getClass().getSimpleName());
    }

    public String getClassLoaderName() {
        return classLoaderName;
    }

    public void setClassLoaderName(String classLoaderName) {
        this.classLoaderName = classLoaderName;
    }

    public Class<?> loadClass(String name, boolean resolve) throws ClassFormatError, ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            System.out.println(getClassLoaderName() + "-loadClass=" + name + ", resolve=" + resolve);

            Class<?> c = findLoadedClass(name);
            System.out.println(getClassLoaderName() + "-findLoadedClass=" + c);
            if (c == null) {
                if (isDelegated(name)) {
                    c = loadClassParentFirst(name, resolve);
                    System.out.println(getClassLoaderName() + "-loadClassParentFirst=" + c);
                }
            }
            if (c == null) {
                try {
                    c = loadClassChildFirst(name);
                } catch (ClassNotFoundException ignored) {
                }
                System.out.println(getClassLoaderName() + "-loadClassChildFirst=" + c);
            }
            if (c == null) {
                if (isChild(name)) {
                    c = loadClassParentFirst(name, resolve);
                    System.out.println(getClassLoaderName() + "-loadClassParentFirst=" + c);
                } else {
                    throw new ClassNotFoundException("not found");
                }
            }

            if (resolve) {
                resolveClass(c);
            }

            return c;
        }
    }

    protected boolean isDelegated(String name) {
//        return isJdkPackage.test(name) || isLogPackage.test(name) || isFastXmlPackage.test(name) || isPinpointTestPackage.test(name);
//        return isJdkPackage.test(name) || isLogPackage.test(name) || isFastXmlPackage.test(name) || isJunitPackage.test(name);
        return isJdkPackage.test(name) || isLogPackage.test(name) || isJunitPackage.test(name);
    }

    protected boolean isChild(String name) {
        return true;
    }

    public Class<?> loadClassParentFirst(String name, boolean resolve) throws ClassNotFoundException {
        return super.loadClass(name, resolve);
    }

    public Class<?> loadClassChildFirst(final String name) throws ClassNotFoundException {
        return findClass(name);
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
        url = super.getResource(name);
        return url;
    }
}
