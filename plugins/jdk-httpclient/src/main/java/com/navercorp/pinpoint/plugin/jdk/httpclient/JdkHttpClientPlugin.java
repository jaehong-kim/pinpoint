/*
 * Copyright 2018 NAVER Corp.
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

package com.navercorp.pinpoint.plugin.jdk.httpclient;

import java.security.ProtectionDomain;

import com.navercorp.pinpoint.bootstrap.async.AsyncContextAccessor;
import com.navercorp.pinpoint.bootstrap.instrument.ClassFilters;
import com.navercorp.pinpoint.bootstrap.instrument.InstrumentClass;
import com.navercorp.pinpoint.bootstrap.instrument.InstrumentException;
import com.navercorp.pinpoint.bootstrap.instrument.InstrumentMethod;
import com.navercorp.pinpoint.bootstrap.instrument.Instrumentor;
import com.navercorp.pinpoint.bootstrap.instrument.MethodFilters;
import com.navercorp.pinpoint.bootstrap.instrument.matcher.Matcher;
import com.navercorp.pinpoint.bootstrap.instrument.matcher.Matchers;
import com.navercorp.pinpoint.bootstrap.instrument.matcher.operand.InterfaceInternalNameMatcherOperand;
import com.navercorp.pinpoint.bootstrap.instrument.transformer.MatchableTransformTemplate;
import com.navercorp.pinpoint.bootstrap.instrument.transformer.MatchableTransformTemplateAware;
import com.navercorp.pinpoint.bootstrap.instrument.transformer.TransformCallback;
import com.navercorp.pinpoint.bootstrap.instrument.transformer.TransformTemplate;
import com.navercorp.pinpoint.bootstrap.instrument.transformer.TransformTemplateAware;
import com.navercorp.pinpoint.bootstrap.interceptor.scope.ExecutionPolicy;
import com.navercorp.pinpoint.bootstrap.logging.PLogger;
import com.navercorp.pinpoint.bootstrap.logging.PLoggerFactory;
import com.navercorp.pinpoint.bootstrap.plugin.ProfilerPlugin;
import com.navercorp.pinpoint.bootstrap.plugin.ProfilerPluginSetupContext;

/**
 * @author jaehong.kim
 */
public class JdkHttpClientPlugin implements ProfilerPlugin, MatchableTransformTemplateAware {
    private final PLogger logger = PLoggerFactory.getLogger(this.getClass());
    private final boolean isInfo = logger.isInfoEnabled();
    private final boolean isDebug = logger.isDebugEnabled();

    private MatchableTransformTemplate transformTemplate;

    @Override
    public void setup(ProfilerPluginSetupContext context) {
        JdkHttpClientPluginConfig config = new JdkHttpClientPluginConfig(context.getConfig());
        if (!config.isEnable()) {
            if (isInfo) {
                logger.info("Disable JdkHttpClientPlugin");
            }
            return;
        }

        if(isInfo) {
            logger.info("Enable JdkHttpClientPlugin. version range=11");
        }

        // Java 11 or later
        addHttpClient();
        addHttpClientLambda();
        addMultiExchange();
    }

    private void addHttpClient() {
        transformTemplate.transform("jdk.internal.net.http.HttpClientImpl", new TransformCallback() {
            @Override
            public byte[] doInTransform(Instrumentor instrumentor, ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws InstrumentException {
                final InstrumentClass target = instrumentor.getInstrumentClass(loader, className, classfileBuffer);
                if(isDebug) {
                    logger.debug("HttpClientImpl nested classes");
                    for(InstrumentClass clazz : target.getNestedClasses(ClassFilters.ACCEPT_ALL)) {
                        logger.debug("className={}, class={}", clazz.getName(), clazz);
                    }
                }

                for (InstrumentMethod method : target.getDeclaredMethods(MethodFilters.name("send", "sendAsync"))) {
                    method.addScopedInterceptor("com.navercorp.pinpoint.plugin.jdk.httpclient.interceptor.HttpClientInterceptor", JdkHttpClientConstants.HTTP_CLIENT_SEND_SCOPE);
                }

                return target.toBytecode();
            }
        });
    }

    private void addHttpClientLambda() {
        final Matcher matcher = Matchers.newPackageBasedMatcher("jdk.internal.net.http.HttpClientImpl$$Lambda$");
        System.out.println("## Matcher " + matcher);
        transformTemplate.transform(matcher, new TransformCallback() {
            @Override
            public byte[] doInTransform(Instrumentor instrumentor, ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws InstrumentException {
                System.out.println("## Transform " + className);
                final InstrumentClass target = instrumentor.getInstrumentClass(loader, className, classfileBuffer);
                target.addField(AsyncContextAccessor.class.getName());
                //  Implementation of java.util.function.BiConsumer
                for(InstrumentMethod method : target.getDeclaredMethods()) {
                    System.out.println("## method " + method.getName() + ", " + method.getDescriptor());
                }

                for (InstrumentMethod method : target.getDeclaredMethods(MethodFilters.name("accept"))) {
                    System.out.println("## find method " + method.getName() + ", " + method.getDescriptor());
                    method.addScopedInterceptor("com.navercorp.pinpoint.plugin.jdk.httpclient.interceptor.HttpClientImplLambdaInterceptor", "HttpClientImplLambda");
                }

                return target.toBytecode();
            }
        });
    }

    private void addMultiExchange() {
        transformTemplate.transform("jdk.internal.net.http.MultiExchange", new TransformCallback() {
            @Override
            public byte[] doInTransform(Instrumentor instrumentor, ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws InstrumentException {

                final InstrumentClass target = instrumentor.getInstrumentClass(loader, className, classfileBuffer);
                final InstrumentMethod constructor = target.getConstructor("java.net.http.HttpRequest", "jdk.internal.net.http.HttpRequestImpl", "jdk.internal.net.http.HttpClientImpl", "java.net.http.HttpResponse$BodyHandler", "java.net.http.HttpResponse$PushPromiseHandler", "java.security.AccessControlContext");
                if (constructor != null) {
                    constructor.addScopedInterceptor("com.navercorp.pinpoint.plugin.jdk.httpclient.interceptor.MultiExchangeInterceptor", JdkHttpClientConstants.HTTP_CLIENT_SEND_SCOPE, ExecutionPolicy.ALWAYS);
                }

                return target.toBytecode();
            }
        });
    }

    @Override
    public void setTransformTemplate(MatchableTransformTemplate transformTemplate) {
        this.transformTemplate = transformTemplate;
    }
}