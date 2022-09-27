package com.navercorp.pinpoint.plugin.httpclient5;

import com.navercorp.pinpoint.bootstrap.async.AsyncContextAccessor;
import com.navercorp.pinpoint.bootstrap.instrument.InstrumentClass;
import com.navercorp.pinpoint.bootstrap.instrument.InstrumentException;
import com.navercorp.pinpoint.bootstrap.instrument.InstrumentMethod;
import com.navercorp.pinpoint.bootstrap.instrument.Instrumentor;
import com.navercorp.pinpoint.bootstrap.instrument.matcher.Matcher;
import com.navercorp.pinpoint.bootstrap.instrument.matcher.Matchers;
import com.navercorp.pinpoint.bootstrap.instrument.matcher.operand.InterfaceInternalNameMatcherOperand;
import com.navercorp.pinpoint.bootstrap.instrument.transformer.*;
import com.navercorp.pinpoint.bootstrap.interceptor.scope.ExecutionPolicy;
import com.navercorp.pinpoint.bootstrap.logging.PLogger;
import com.navercorp.pinpoint.bootstrap.logging.PLoggerFactory;
import com.navercorp.pinpoint.bootstrap.plugin.ProfilerPlugin;
import com.navercorp.pinpoint.bootstrap.plugin.ProfilerPluginSetupContext;
import com.navercorp.pinpoint.plugin.httpclient5.interceptor.HttpClientResponseHandlerHandleResponseInterceptor;
import com.navercorp.pinpoint.plugin.httpclient5.interceptor.InternalHttpClientDoExecuteInterceptor;

import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;

public class HttpClient5Plugin implements ProfilerPlugin, MatchableTransformTemplateAware {
    private final PLogger logger = PLoggerFactory.getLogger(this.getClass());

    private MatchableTransformTemplate transformTemplate;

    @Override
    public void setup(ProfilerPluginSetupContext context) {
        final HttpClient5PluginConfig config = new HttpClient5PluginConfig(context.getConfig());

        //  basePackageName of HttpClientResponseHandler interface
        final List<String> basePackageNames = filterBasePackageNames(config.getHttpClientResponseHandlerBasePackageNames());
        if (Boolean.FALSE == basePackageNames.isEmpty()) {
            addHandlerInterceptor(basePackageNames);
        }
        // default HttpClientResponseHandler implementations
        transformTemplate.transform("org.apache.hc.client5.http.impl.classic.AbstractHttpClientResponseHandler", InternalHttpClientTransform.class);
        transformTemplate.transform("org.apache.hc.client5.http.impl.classic.BasicHttpClientResponseHandler", InternalHttpClientTransform.class);


        // Apache httpclient5
        transformTemplate.transform("org.apache.hc.client5.http.impl.classic.InternalHttpClient", InternalHttpClientTransform.class);
        transformTemplate.transform("org.apache.hc.client5.http.impl.classic.CloseableHttpResponse", CloseableHttpResponseTransform.class);


    }

    List<String> filterBasePackageNames(List<String> basePackageNames) {
        final List<String> list = new ArrayList<>();
        for (String basePackageName : basePackageNames) {
            final String name = basePackageName.trim();
            if (!name.isEmpty()) {
                list.add(name);
            }
        }
        return list;
    }

    private void addHandlerInterceptor(final List<String> basePackageNames) {
        // basepackageNames AND io.vertx.core.Handler
        final Matcher matcher = Matchers.newPackageBasedMatcher(basePackageNames, new InterfaceInternalNameMatcherOperand("org.apache.hc.core5.http.io.HttpClientResponseHandler", true));
        transformTemplate.transform(matcher, HttpClientResponseHandlerTransform.class);
    }

    public static class HttpClientResponseHandlerTransform implements TransformCallback {

        @Override
        public byte[] doInTransform(Instrumentor instrumentor, ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws InstrumentException {
            InstrumentClass target = instrumentor.getInstrumentClass(loader, className, classfileBuffer);
            target.addField(AsyncContextAccessor.class);
            final InstrumentMethod handleMethod = target.getDeclaredMethod("handleResponse", "org.apache.hc.core5.http.ClassicHttpResponse");
            if (handleMethod != null) {
                handleMethod.addInterceptor(HttpClientResponseHandlerHandleResponseInterceptor.class);
            }
            return target.toBytecode();
        }
    }

    public static class InternalHttpClientTransform implements TransformCallback {

        @Override
        public byte[] doInTransform(Instrumentor instrumentor, ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws InstrumentException {
            InstrumentClass target = instrumentor.getInstrumentClass(loader, className, classfileBuffer);

            InstrumentMethod execute = target.getDeclaredMethod("doExecute", "org.apache.hc.core5.http.HttpHost", "org.apache.hc.core5.http.ClassicHttpRequest", "org.apache.hc.core5.http.protocol.HttpContext");
            if (execute != null) {
                execute.addInterceptor(InternalHttpClientDoExecuteInterceptor.class);
            }

            return target.toBytecode();
        }
    }

    public static class CloseableHttpResponseTransform implements TransformCallback {

        @Override
        public byte[] doInTransform(Instrumentor instrumentor, ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws InstrumentException {
            InstrumentClass target = instrumentor.getInstrumentClass(loader, className, classfileBuffer);
            target.addField(AsyncContextAccessor.class);
            InstrumentMethod execute = target.getDeclaredMethod("doExecute", "org.apache.hc.core5.http.HttpHost", "org.apache.hc.core5.http.ClassicHttpRequest", "org.apache.hc.core5.http.protocol.HttpContext");
            if (execute != null) {
                execute.addInterceptor(InternalHttpClientDoExecuteInterceptor.class);
            }

            return target.toBytecode();
        }
    }

    @Override
    public void setTransformTemplate(MatchableTransformTemplate transformTemplate) {
        this.transformTemplate = transformTemplate;
    }
}
