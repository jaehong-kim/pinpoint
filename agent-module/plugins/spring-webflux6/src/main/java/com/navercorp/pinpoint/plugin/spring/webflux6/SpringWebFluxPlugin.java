/*
 * Copyright 2024 NAVER Corp.
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
package com.navercorp.pinpoint.plugin.spring.webflux6;

import com.navercorp.pinpoint.bootstrap.async.AsyncContextAccessor;
import com.navercorp.pinpoint.bootstrap.instrument.InstrumentClass;
import com.navercorp.pinpoint.bootstrap.instrument.InstrumentException;
import com.navercorp.pinpoint.bootstrap.instrument.InstrumentMethod;
import com.navercorp.pinpoint.bootstrap.instrument.Instrumentor;
import com.navercorp.pinpoint.bootstrap.instrument.matcher.Matcher;
import com.navercorp.pinpoint.bootstrap.instrument.matcher.Matchers;
import com.navercorp.pinpoint.bootstrap.instrument.matcher.operand.MatcherOperand;
import com.navercorp.pinpoint.bootstrap.instrument.matcher.operand.VersionMatcherOperand;
import com.navercorp.pinpoint.bootstrap.instrument.transformer.MatchableTransformTemplate;
import com.navercorp.pinpoint.bootstrap.instrument.transformer.MatchableTransformTemplateAware;
import com.navercorp.pinpoint.bootstrap.instrument.transformer.TransformCallback;
import com.navercorp.pinpoint.bootstrap.instrument.transformer.TransformCallbackParametersBuilder;
import com.navercorp.pinpoint.bootstrap.logging.PluginLogManager;
import com.navercorp.pinpoint.bootstrap.logging.PluginLogger;
import com.navercorp.pinpoint.bootstrap.plugin.ProfilerPlugin;
import com.navercorp.pinpoint.bootstrap.plugin.ProfilerPluginSetupContext;
import com.navercorp.pinpoint.plugin.spring.webflux6.interceptor.AbstractHandlerMethodMappingInterceptor;
import com.navercorp.pinpoint.plugin.spring.webflux6.interceptor.AbstractUrlHandlerMappingInterceptor;
import com.navercorp.pinpoint.plugin.spring.webflux6.interceptor.BodyInserterRequestBuilderWriteToInterceptor;
import com.navercorp.pinpoint.plugin.spring.webflux6.interceptor.ClientResponseFunctionInterceptor;
import com.navercorp.pinpoint.plugin.spring.webflux6.interceptor.DefaultWebClientExchangeMethodInterceptor;
import com.navercorp.pinpoint.plugin.spring.webflux6.interceptor.DispatchHandlerGetLambdaInterceptor;
import com.navercorp.pinpoint.plugin.spring.webflux6.interceptor.DispatchHandlerHandleMethodInterceptor;
import com.navercorp.pinpoint.plugin.spring.webflux6.interceptor.DispatchHandlerInvokeHandlerMethodInterceptor;
import com.navercorp.pinpoint.plugin.spring.webflux6.interceptor.ExchangeFunctionMethodInterceptor;
import com.navercorp.pinpoint.plugin.spring.webflux6.interceptor.InvocableHandlerMethodInterceptor;

import java.security.ProtectionDomain;

import static com.navercorp.pinpoint.common.util.VarArgs.va;

public class SpringWebFluxPlugin implements ProfilerPlugin, MatchableTransformTemplateAware {
    private final PluginLogger logger = PluginLogManager.getLogger(getClass());
    private MatchableTransformTemplate transformTemplate;

    @Override
    public void setup(ProfilerPluginSetupContext context) {
        final SpringWebFluxPluginConfig config = new SpringWebFluxPluginConfig(context.getConfig());
        if (Boolean.FALSE == config.isEnable()) {
            logger.info("{} disabled", this.getClass().getSimpleName());
            return;
        }

        final MatcherOperand versionMatcherOperand = new VersionMatcherOperand("[6.0.0,6.max]", config.isVersionForcedMatch());
        logger.info("{}, version {}, config:{}", this.getClass().getSimpleName(), versionMatcherOperand, config);

        // Server
        final Matcher dispatchHandlerTransformMatcher = Matchers.newClassBasedMatcher("org.springframework.web.reactive.DispatcherHandler", versionMatcherOperand);
        transformTemplate.transform(dispatchHandlerTransformMatcher, DispatchHandlerTransform.class,
                TransformCallbackParametersBuilder.newBuilder()
                        .addBoolean(config.isUriStatEnable())
                        .addBoolean(config.isUriStatUseUserInput())
                        .toParameters());
        final Matcher dispatchHandlerInvokeHandlerTransformMatcher = Matchers.newLambdaExpressionMatcher("org.springframework.web.reactive.DispatcherHandler", "java.util.function.Function", versionMatcherOperand);
        transformTemplate.transform(dispatchHandlerInvokeHandlerTransformMatcher, DispatchHandlerInvokeHandlerTransform.class);

        final Matcher serverWebExchangeTransformMatcher = Matchers.newClassBasedMatcher("org.springframework.web.server.adapter.DefaultServerWebExchange", versionMatcherOperand);
        transformTemplate.transform(serverWebExchangeTransformMatcher, ServerWebExchangeTransform.class);
        final Matcher invocableHandlerMethodTransformMatcher = Matchers.newClassBasedMatcher("org.springframework.web.reactive.result.method.InvocableHandlerMethod", versionMatcherOperand);
        transformTemplate.transform(invocableHandlerMethodTransformMatcher, InvocableHandlerMethodTransform.class);

        // Client
        if (Boolean.TRUE == config.isClientEnable()) {
            // If there is a conflict with Reactor-Netty, set it to false.
            final Matcher defaultWebClientTransformMatcher = Matchers.newClassBasedMatcher("org.springframework.web.reactive.function.client.DefaultWebClient$DefaultRequestBodyUriSpec", versionMatcherOperand);
            transformTemplate.transform(defaultWebClientTransformMatcher, DefaultWebClientTransform.class);
            final Matcher exchangeFunctionTransformMatcher = Matchers.newClassBasedMatcher("org.springframework.web.reactive.function.client.ExchangeFunctions$DefaultExchangeFunction", versionMatcherOperand);
            transformTemplate.transform(exchangeFunctionTransformMatcher, ExchangeFunctionTransform.class);
            final Matcher bodyInserterRequestTransformMatcher = Matchers.newClassBasedMatcher("org.springframework.web.reactive.function.client.DefaultClientRequestBuilder$BodyInserterRequest", versionMatcherOperand);
            transformTemplate.transform(bodyInserterRequestTransformMatcher, BodyInserterRequestTransform.class);
        }

        // uri stat
        if (config.isUriStatEnable()) {
            final Matcher abstractHandlerMethodMappingTransformMatcher = Matchers.newClassBasedMatcher("org.springframework.web.reactive.result.method.AbstractHandlerMethodMapping", versionMatcherOperand);
            transformTemplate.transform(abstractHandlerMethodMappingTransformMatcher, AbstractHandlerMethodMappingTransform.class,
                    TransformCallbackParametersBuilder.newBuilder()
                            .addBoolean(config.isUriStatCollectMethod())
                            .toParameters());
            final Matcher abstractUrlHandlerMappingTransformMatcher = Matchers.newClassBasedMatcher("org.springframework.web.reactive.handler.AbstractUrlHandlerMapping", versionMatcherOperand);
            transformTemplate.transform(abstractUrlHandlerMappingTransformMatcher, AbstractUrlHandlerMappingTransform.class,
                    TransformCallbackParametersBuilder.newBuilder()
                            .addBoolean(config.isUriStatCollectMethod())
                            .toParameters());
        }
    }

    @Override
    public void setTransformTemplate(MatchableTransformTemplate transformTemplate) {
        this.transformTemplate = transformTemplate;
    }

    public static class DispatchHandlerTransform implements TransformCallback {
        private final Boolean uriStatEnable;
        private final Boolean uriStatUseUserInput;

        public DispatchHandlerTransform(Boolean uriStatEnable, Boolean uriStatUseUserInput) {
            this.uriStatEnable = uriStatEnable;
            this.uriStatUseUserInput = uriStatUseUserInput;
        }

        @Override
        public byte[] doInTransform(Instrumentor instrumentor, ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws InstrumentException {
            final InstrumentClass target = instrumentor.getInstrumentClass(loader, className, classfileBuffer);

            // Dispatch
            final InstrumentMethod handleMethod = target.getDeclaredMethod("handle", "org.springframework.web.server.ServerWebExchange");
            if (handleMethod != null) {
                handleMethod.addInterceptor(DispatchHandlerHandleMethodInterceptor.class);
            }
            // Invoke
            final InstrumentMethod invokerHandlerMethod = target.getDeclaredMethod("invokeHandler", "org.springframework.web.server.ServerWebExchange", "java.lang.Object");
            if (invokerHandlerMethod != null) {
                invokerHandlerMethod.addInterceptor(DispatchHandlerInvokeHandlerMethodInterceptor.class, va(this.uriStatEnable, Boolean.valueOf(false)));
            }
            // Result
            final InstrumentMethod handleResultMethod = target.getDeclaredMethod("handleResult", "org.springframework.web.server.ServerWebExchange", "org.springframework.web.reactive.HandlerResult");
            if (handleResultMethod != null) {
                handleResultMethod.addInterceptor(DispatchHandlerInvokeHandlerMethodInterceptor.class, va(this.uriStatEnable, this.uriStatUseUserInput));
            }

            return target.toBytecode();
        }
    }

    public static class DispatchHandlerInvokeHandlerTransform implements TransformCallback {
        @Override
        public byte[] doInTransform(Instrumentor instrumentor, ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws InstrumentException {
            final InstrumentClass target = instrumentor.getInstrumentClass(loader, className, classfileBuffer);
            // Async Object
            target.addField(AsyncContextAccessor.class);

            // flatMap(handler -> invokeHandler(exchange, handler))
            // flatMap(result -> handleResult(exchange, result))
            InstrumentMethod handlerAndResultGetLambdaMethod = target.getConstructor("org.springframework.web.reactive.DispatcherHandler", "org.springframework.web.server.ServerWebExchange");
            if (handlerAndResultGetLambdaMethod != null) {
                handlerAndResultGetLambdaMethod.addInterceptor(DispatchHandlerGetLambdaInterceptor.class);
            }

            return target.toBytecode();
        }
    }

    public static class ServerWebExchangeTransform implements TransformCallback {
        @Override
        public byte[] doInTransform(Instrumentor instrumentor, ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws InstrumentException {
            final InstrumentClass target = instrumentor.getInstrumentClass(loader, className, classfileBuffer);
            // Async Object
            target.addField(AsyncContextAccessor.class);

            return target.toBytecode();
        }
    }

    public static class InvocableHandlerMethodTransform implements TransformCallback {
        @Override
        public byte[] doInTransform(Instrumentor instrumentor, ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws InstrumentException {
            final InstrumentClass target = instrumentor.getInstrumentClass(loader, className, classfileBuffer);
            final InstrumentMethod invokerMethod = target.getDeclaredMethod("invoke", "org.springframework.web.server.ServerWebExchange", "org.springframework.web.reactive.BindingContext", "java.lang.Object[]");
            if (invokerMethod != null) {
                invokerMethod.addInterceptor(InvocableHandlerMethodInterceptor.class);
            }

            return target.toBytecode();
        }
    }

    public static class DefaultWebClientTransform implements TransformCallback {
        @Override
        public byte[] doInTransform(Instrumentor instrumentor, ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws InstrumentException {
            final InstrumentClass target = instrumentor.getInstrumentClass(loader, className, classfileBuffer);
            // Set AsyncContext
            final InstrumentMethod exchangeMethod = target.getDeclaredMethod("exchange");
            if (exchangeMethod != null) {
                exchangeMethod.addInterceptor(DefaultWebClientExchangeMethodInterceptor.class);
            }

            return target.toBytecode();
        }
    }

    public static class ExchangeFunctionTransform implements TransformCallback {
        @Override
        public byte[] doInTransform(Instrumentor instrumentor, ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws InstrumentException {
            final InstrumentClass target = instrumentor.getInstrumentClass(loader, className, classfileBuffer);
            // Set AsyncContext
            final InstrumentMethod exchangeMethod = target.getDeclaredMethod("exchange", "org.springframework.web.reactive.function.client.ClientRequest");
            if (exchangeMethod != null) {
                exchangeMethod.addInterceptor(ExchangeFunctionMethodInterceptor.class);
            }

            final InstrumentMethod logResponseMethod = target.getDeclaredMethod("logResponse", "org.springframework.http.client.reactive.ClientHttpResponse", "java.lang.String");
            if (logResponseMethod != null) {
                logResponseMethod.addInterceptor(ClientResponseFunctionInterceptor.class);
            }

            return target.toBytecode();
        }
    }

    public static class BodyInserterRequestTransform implements TransformCallback {
        @Override
        public byte[] doInTransform(Instrumentor instrumentor, ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws InstrumentException {
            final InstrumentClass target = instrumentor.getInstrumentClass(loader, className, classfileBuffer);
            target.addField(AsyncContextAccessor.class);

            // RPC
            final InstrumentMethod method = target.getDeclaredMethod("writeTo", "org.springframework.http.client.reactive.ClientHttpRequest", "org.springframework.web.reactive.function.client.ExchangeStrategies");
            if (method != null) {
                method.addInterceptor(BodyInserterRequestBuilderWriteToInterceptor.class);
            }

            return target.toBytecode();
        }
    }

    public static class AbstractHandlerMethodMappingTransform implements TransformCallback {
        private final Boolean uriStatCollectMethod;

        public AbstractHandlerMethodMappingTransform(Boolean uriStatCollectMethod) {
            this.uriStatCollectMethod = uriStatCollectMethod;
        }

        @Override
        public byte[] doInTransform(Instrumentor instrumentor, ClassLoader classLoader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws InstrumentException {
            final InstrumentClass target = instrumentor.getInstrumentClass(classLoader, className, classfileBuffer);

            // Add attribute listener.
            final InstrumentMethod lookupHandlerMethod = target.getDeclaredMethod("lookupHandlerMethod", "org.springframework.web.server.ServerWebExchange");
            if (lookupHandlerMethod != null) {
                lookupHandlerMethod.addInterceptor(AbstractHandlerMethodMappingInterceptor.class, va(uriStatCollectMethod));
            }
            return target.toBytecode();
        }
    }

    public static class AbstractUrlHandlerMappingTransform implements TransformCallback {
        private final Boolean uriStatCollectMethod;

        public AbstractUrlHandlerMappingTransform(Boolean uriStatCollectMethod) {
            this.uriStatCollectMethod = uriStatCollectMethod;
        }

        @Override
        public byte[] doInTransform(Instrumentor instrumentor, ClassLoader classLoader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws InstrumentException {
            final InstrumentClass target = instrumentor.getInstrumentClass(classLoader, className, classfileBuffer);

            // Add attribute listener.
            final InstrumentMethod exposePathWithinMapping = target.getDeclaredMethod("lookupHandler", "org.springframework.http.server.PathContainer", "org.springframework.web.server.ServerWebExchange");
            if (exposePathWithinMapping != null) {
                exposePathWithinMapping.addInterceptor(AbstractUrlHandlerMappingInterceptor.class, va(uriStatCollectMethod));
            }
            return target.toBytecode();
        }
    }

}
