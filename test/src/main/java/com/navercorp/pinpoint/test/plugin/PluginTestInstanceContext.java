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

import com.navercorp.pinpoint.bootstrap.plugin.test.PluginTestVerifier;
import com.navercorp.pinpoint.profiler.interceptor.registry.InterceptorRegistryBinder;
import com.navercorp.pinpoint.test.plugin.agent.classloader.Translator;
import com.navercorp.pinpoint.test.plugin.classloader.PluginAgentClassLoader;

public class PluginTestInstanceContext {

    private PluginAgentClassLoader pluginAgentClassLoader;
    private ClassLoader classLoader;
    private PluginTestVerifier pluginTestVerifier;
    private InterceptorRegistryBinder interceptorRegistryBinder;

    private Translator translator;

    public PluginAgentClassLoader getPluginAgentClassLoader() {
        return pluginAgentClassLoader;
    }

    public void setPluginAgentClassLoader(PluginAgentClassLoader pluginAgentClassLoader) {
        this.pluginAgentClassLoader = pluginAgentClassLoader;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public PluginTestVerifier getPluginTestVerifier() {
        return pluginTestVerifier;
    }

    public void setPluginTestVerifier(PluginTestVerifier pluginTestVerifier) {
        this.pluginTestVerifier = pluginTestVerifier;
    }

    public InterceptorRegistryBinder getInterceptorRegistryBinder() {
        return interceptorRegistryBinder;
    }

    public void setInterceptorRegistryBinder(InterceptorRegistryBinder interceptorRegistryBinder) {
        this.interceptorRegistryBinder = interceptorRegistryBinder;
    }

    public Translator getTranslator() {
        return translator;
    }

    public void setTranslator(Translator translator) {
        this.translator = translator;
    }
}
