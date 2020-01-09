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

package com.navercorp.pinpoint.plugin.jdk.httpclient.interceptor;

import com.navercorp.pinpoint.bootstrap.async.AsyncContextAccessor;
import com.navercorp.pinpoint.bootstrap.config.HttpDumpConfig;
import com.navercorp.pinpoint.bootstrap.context.MethodDescriptor;
import com.navercorp.pinpoint.bootstrap.context.SpanEventRecorder;
import com.navercorp.pinpoint.bootstrap.context.Trace;
import com.navercorp.pinpoint.bootstrap.context.TraceContext;
import com.navercorp.pinpoint.bootstrap.context.TraceId;
import com.navercorp.pinpoint.bootstrap.interceptor.AroundInterceptor;
import com.navercorp.pinpoint.bootstrap.interceptor.scope.InterceptorScope;
import com.navercorp.pinpoint.bootstrap.logging.PLogger;
import com.navercorp.pinpoint.bootstrap.logging.PLoggerFactory;
import com.navercorp.pinpoint.bootstrap.plugin.request.ClientHeaderAdaptor;
import com.navercorp.pinpoint.bootstrap.plugin.request.ClientRequestAdaptor;
import com.navercorp.pinpoint.bootstrap.plugin.request.ClientRequestRecorder;
import com.navercorp.pinpoint.bootstrap.plugin.request.DefaultRequestTraceWriter;
import com.navercorp.pinpoint.bootstrap.plugin.request.RequestTraceWriter;
import com.navercorp.pinpoint.bootstrap.plugin.request.util.CookieExtractor;
import com.navercorp.pinpoint.bootstrap.plugin.request.util.CookieRecorder;
import com.navercorp.pinpoint.bootstrap.plugin.request.util.CookieRecorderFactory;
import com.navercorp.pinpoint.bootstrap.plugin.request.util.EntityExtractor;
import com.navercorp.pinpoint.bootstrap.plugin.request.util.EntityRecorder;
import com.navercorp.pinpoint.bootstrap.plugin.request.util.EntityRecorderFactory;
import com.navercorp.pinpoint.plugin.jdk.httpclient.HttpRequestImplClientHeaderAdaptor;
import com.navercorp.pinpoint.plugin.jdk.httpclient.HttpRequestImplClientRequestAdaptor;
import com.navercorp.pinpoint.plugin.jdk.httpclient.JdkHttpClientConstants;
import com.navercorp.pinpoint.plugin.jdk.httpclient.JdkHttpClientCookieExtractor;
import com.navercorp.pinpoint.plugin.jdk.httpclient.JdkHttpClientEntityExtractor;
import com.navercorp.pinpoint.plugin.jdk.httpclient.JdkHttpClientPluginConfig;
import jdk.internal.net.http.HttpRequestImpl;

import java.net.URI;

/**
 * @author jaehong.kim
 */
public class MultiExchangeInterceptor implements AroundInterceptor {
    private static final Object TRACE_BLOCK_BEGIN_MARKER = new Object();
    private final PLogger logger = PLoggerFactory.getLogger(this.getClass());
    private final boolean isDebug = logger.isDebugEnabled();

    private final TraceContext traceContext;
    private final MethodDescriptor descriptor;
    private final InterceptorScope scope;
    private final ClientRequestRecorder<HttpRequestImpl> clientRequestRecorder;

    private final RequestTraceWriter<HttpRequestImpl> requestTraceWriter;
    private final CookieRecorder<HttpRequestImpl> cookieRecorder;
    private final EntityRecorder<HttpRequestImpl> entityRecorder;

    public MultiExchangeInterceptor(TraceContext traceContext, MethodDescriptor descriptor, InterceptorScope scope) {
        this.traceContext = traceContext;
        this.descriptor = descriptor;
        this.scope = scope;

        final JdkHttpClientPluginConfig config = new JdkHttpClientPluginConfig(traceContext.getProfilerConfig());
        final HttpDumpConfig httpDumpConfig = config.getHttpDumpConfig();

        final ClientRequestAdaptor<HttpRequestImpl> clientRequestAdaptor = new HttpRequestImplClientRequestAdaptor();
        this.clientRequestRecorder = new ClientRequestRecorder<HttpRequestImpl>(config.isParam(), clientRequestAdaptor);

        final ClientHeaderAdaptor<HttpRequestImpl> clientHeaderAdaptor = new HttpRequestImplClientHeaderAdaptor();
        this.requestTraceWriter = new DefaultRequestTraceWriter<HttpRequestImpl>(clientHeaderAdaptor, traceContext);

        final CookieExtractor<HttpRequestImpl> cookieExtractor = JdkHttpClientCookieExtractor.INSTANCE;
        this.cookieRecorder = CookieRecorderFactory.newCookieRecorder(httpDumpConfig, cookieExtractor);

        final EntityExtractor<HttpRequestImpl> entityExtractor = JdkHttpClientEntityExtractor.INSTANCE;
        this.entityRecorder = EntityRecorderFactory.newEntityRecorder(httpDumpConfig, entityExtractor);
    }

    @Override
    public void before(Object target, Object[] args) {
        if (isDebug) {
            logger.beforeInterceptor(target, args);
        }

        Trace trace = traceContext.currentRawTraceObject();
        if (trace == null) {
            return;
        }

        if (!validate(args)) {
            return;
        }

        try {
            final HttpRequestImpl request = (HttpRequestImpl) args[1];
            final boolean sampling = trace.canSampled();
            if (!sampling) {
                if (request != null) {
                    this.requestTraceWriter.write(request);
                }
                return;
            }

            scope.getCurrentInvocation().setAttachment(TRACE_BLOCK_BEGIN_MARKER);

            final SpanEventRecorder recorder = trace.traceBlockBegin();
            recorder.recordServiceType(JdkHttpClientConstants.SERVICE_TYPE);
            final TraceId nextId = trace.getTraceId().getNextTraceId();
            recorder.recordNextSpanId(nextId.getSpanId());

            if (request != null) {
                String host = getHost(request);
                this.requestTraceWriter.write(request, nextId, host);
            }
        } catch (Throwable t) {
            logger.warn("Failed to BEFORE", t);
        }
    }


    private String getHost(HttpRequestImpl httpRequest) {
        final URI uri = httpRequest.uri();
        if (uri != null) {
            final String host = uri.getHost();
            final int port = uri.getPort();
            if (host != null) {
                return HttpRequestImplClientRequestAdaptor.getEndpoint(host, port);
            }
        }
        return null;
    }

    @Override
    public void after(Object target, Object[] args, Object result, Throwable throwable) {
        if (isDebug) {
            // do not log result
            logger.afterInterceptor(target, args);
        }

        Trace trace = traceContext.currentTraceObject();
        if (trace == null) {
            return;
        }

        Object marker = scope.getCurrentInvocation().getAttachment();
        if (marker != TRACE_BLOCK_BEGIN_MARKER) {
            return;
        }

        if (!validate(args)) {
            return;
        }

        try {
            final SpanEventRecorder recorder = trace.currentSpanEventRecorder();
            recorder.recordApi(descriptor);
            recorder.recordException(throwable);
            final HttpRequestImpl request = (HttpRequestImpl) args[1];
            if (request != null) {
                this.clientRequestRecorder.record(recorder, request, throwable);
                this.cookieRecorder.record(recorder, request, throwable);
                this.entityRecorder.record(recorder, request, throwable);
            }
        } finally {
            trace.traceBlockEnd();
        }
    }

    private boolean validate(final Object[] args) {
        if (args == null || args.length < 2) {
            if (isDebug) {
                logger.debug("Invalid args object. args={}.", args);
            }
            return false;
        }

        if (!(args[1] instanceof HttpRequestImpl)) {
            if (isDebug) {
                logger.debug("Invalid args[1] object. args[1]={}", args[1]);
            }
            return false;
        }

        return true;
    }


}