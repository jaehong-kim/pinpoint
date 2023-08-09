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

package com.navercorp.pinpoint.test.plugin.agent;

import com.navercorp.pinpoint.bootstrap.plugin.test.PluginTestVerifier;
import com.navercorp.pinpoint.bootstrap.plugin.test.PluginTestVerifierHolder;
import com.navercorp.pinpoint.profiler.context.module.DefaultApplicationContext;
import com.navercorp.pinpoint.profiler.interceptor.registry.InterceptorRegistryBinder;
import com.navercorp.pinpoint.test.plugin.PluginTestInstanceCallback;
import com.navercorp.pinpoint.test.plugin.PluginTestInstanceContext;
import com.navercorp.pinpoint.test.plugin.agent.classloader.DefaultTranslator;
import com.navercorp.pinpoint.test.plugin.agent.classloader.Translator;

public class PluginTestAgentStarter {

    private PluginTestInstanceContext context = new PluginTestInstanceContext();

    private Translator translator;
    private InterceptorRegistryBinder interceptorRegistryBinder;
    private PluginTestVerifier pluginTestVerifier;

    public PluginTestAgentStarter(String configFile, ClassLoader classLoader) {
        final MockApplicationContextFactory factory = new MockApplicationContextFactory();
        final DefaultApplicationContext applicationContext = factory.build(configFile);
        this.translator = new DefaultTranslator(classLoader, applicationContext.getClassFileTransformer());
        this.interceptorRegistryBinder = applicationContext.getInterceptorRegistryBinder();
        this.pluginTestVerifier = new PluginVerifierExternalAdaptor(applicationContext);


//        context.setTranslator(translator);
//        context.setPluginTestVerifier(pluginVerifier);
//        context.setInterceptorRegistryBinder(interceptorRegistryBinder);
    }

    public PluginTestInstanceCallback getCallback() {
        return new PluginTestInstanceCallback() {
            @Override
            public byte[] transform(ClassLoader classLoader, String name, byte[] classfileBuffer) throws ClassNotFoundException {
                return translator.transform(classLoader, name, classfileBuffer);
            }

            @Override
            public void before(boolean verify, boolean manageTraceObject) {
                interceptorRegistryBinder.bind();
                if (verify) {
                    PluginTestVerifierHolder.setInstance(pluginTestVerifier);
                    pluginTestVerifier.initialize(manageTraceObject);
                }
            }

            @Override
            public void after(boolean verify, boolean manageTraceObject) {
                interceptorRegistryBinder.unbind();
                if (verify) {
                    pluginTestVerifier.cleanUp(manageTraceObject);
                    PluginTestVerifierHolder.setInstance(null);
                }
            }
        };
    }


    public PluginTestInstanceContext getContext() {
        return context;
    }
}
