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

package com.navercorp.pinpoint.test.plugin.shared;

public class PluginSharedInstance {
//    final SharedTestExecutor sharedTestExecutor;

    private String className;
    private String sharedClassName;
    private ClassLoader classLoader;
    private Class<?> sharedClass;

    Object object = null;

    public PluginSharedInstance(String className, String sharedClassName, ClassLoader classLoader) {
//        this.sharedTestExecutor = new SharedTestExecutor(className, classLoader);
        this.className = className;
        this.sharedClassName = sharedClassName;
        this.classLoader = classLoader;
//        this.classLoader = Thread.currentThread().getContextClassLoader();
    }

    public void before() {
        try {
            Class<?> testClass = classLoader.loadClass(className);
            this.sharedClass = classLoader.loadClass(sharedClassName);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        Thread thread = Thread.currentThread();
        final ClassLoader currentClassLoader = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(this.classLoader);
            try {
                this.object = sharedClass.newInstance();
            } catch (InstantiationException e) {
                throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
            if (object instanceof SharedTestLifeCycle) {
                ((SharedTestLifeCycle) object).beforeAll();
            }
        } finally {
            thread.setContextClassLoader(currentClassLoader);
        }


//        sharedTestExecutor.startBefore(10, TimeUnit.MINUTES);
    }

    public void after() {
//        sharedTestExecutor.startAfter(5, TimeUnit.MINUTES);
        if (object instanceof SharedTestLifeCycle) {
            ((SharedTestLifeCycle) object).afterAll();
        }
    }
}
