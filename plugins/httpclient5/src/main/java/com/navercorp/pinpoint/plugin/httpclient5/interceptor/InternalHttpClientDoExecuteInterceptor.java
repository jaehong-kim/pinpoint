package com.navercorp.pinpoint.plugin.httpclient5.interceptor;

import com.navercorp.pinpoint.bootstrap.async.AsyncContextAccessor;
import com.navercorp.pinpoint.bootstrap.config.HttpDumpConfig;
import com.navercorp.pinpoint.bootstrap.context.*;
import com.navercorp.pinpoint.bootstrap.interceptor.AroundInterceptor;
import com.navercorp.pinpoint.bootstrap.logging.PLogger;
import com.navercorp.pinpoint.bootstrap.logging.PLoggerFactory;
import com.navercorp.pinpoint.bootstrap.pair.NameIntValuePair;
import com.navercorp.pinpoint.bootstrap.plugin.request.*;
import com.navercorp.pinpoint.bootstrap.plugin.request.util.*;
import com.navercorp.pinpoint.common.plugin.util.HostAndPort;
import com.navercorp.pinpoint.common.util.ArrayArgumentUtils;
import com.navercorp.pinpoint.plugin.httpclient5.*;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;

public class InternalHttpClientDoExecuteInterceptor implements AroundInterceptor {
    private final PLogger logger = PLoggerFactory.getLogger(this.getClass());
    private final boolean isDebug = logger.isDebugEnabled();

    private final TraceContext traceContext;
    private final MethodDescriptor methodDescriptor;
    private final ClientRequestRecorder<ClientRequestWrapper> clientRequestRecorder;
    private final CookieRecorder<HttpRequest> cookieRecorder;
    private final EntityRecorder<HttpRequest> entityRecorder;

    private final RequestTraceWriter<HttpRequest> requestTraceWriter;

    public InternalHttpClientDoExecuteInterceptor(TraceContext traceContext, MethodDescriptor methodDescriptor) {
        this.traceContext = traceContext;
        this.methodDescriptor = methodDescriptor;

        final HttpClient5PluginConfig config = new HttpClient5PluginConfig(traceContext.getProfilerConfig());
        final boolean param = config.isParam();
        final HttpDumpConfig httpDumpConfig = config.getHttpDumpConfig();

        ClientRequestAdaptor<ClientRequestWrapper> clientRequestAdaptor = ClientRequestWrapperAdaptor.INSTANCE;
        this.clientRequestRecorder = new ClientRequestRecorder<>(param, clientRequestAdaptor);

        CookieExtractor<HttpRequest> cookieExtractor = HttpClient5CookieExtractor.INSTANCE;
        this.cookieRecorder = CookieRecorderFactory.newCookieRecorder(httpDumpConfig, cookieExtractor);

        EntityExtractor<HttpRequest> entityExtractor = HttpClient5EntityExtractor.INSTANCE;
        this.entityRecorder = EntityRecorderFactory.newEntityRecorder(httpDumpConfig, entityExtractor);

        ClientHeaderAdaptor<HttpRequest> clientHeaderAdaptor = new HttpRequest5ClientHeaderAdaptor();
        this.requestTraceWriter = new DefaultRequestTraceWriter<>(clientHeaderAdaptor, traceContext);
    }

    @Override
    public void before(Object target, Object[] args) {
        if (isDebug) {
            logger.beforeInterceptor(target, args);
        }

        final Trace trace = traceContext.currentRawTraceObject();
        if (trace == null) {
            return;
        }

        try {
            final HttpHost httpHost = ArrayArgumentUtils.getArgument(args, 0, HttpHost.class);
            if (httpHost == null) {
                return;
            }
            final NameIntValuePair<String> host = getHost(httpHost);
            final HttpRequest httpRequest = ArrayArgumentUtils.getArgument(args, 1, HttpRequest.class);
            if (httpRequest == null) {
                return;
            }

            final boolean sampling = trace.canSampled();
            if (!sampling) {
                if (httpRequest != null) {
                    this.requestTraceWriter.write(httpRequest);
                }
                return;
            }

            final SpanEventRecorder recorder = trace.traceBlockBegin();
            // set remote trace
            final TraceId nextId = trace.getTraceId().getNextTraceId();
            recorder.recordNextSpanId(nextId.getSpanId());
            recorder.recordServiceType(HttpClient5Constants.HTTP_CLIENT5);

            final String hostString = getHostString(host.getName(), host.getValue());
            this.requestTraceWriter.write(httpRequest, nextId, hostString);
        } catch (Throwable t) {
            logger.warn("Failed to BEFORE process. {}", t.getMessage(), t);
        }
    }


    private String getHostString(String hostName, int port) {
        if (hostName != null) {
            return HostAndPort.toHostAndPortString(hostName, port);
        }
        return null;
    }

    private NameIntValuePair<String> getHost(final HttpHost httpHost) {
        return new NameIntValuePair<>(httpHost.getHostName(), httpHost.getPort());
    }

    @Override
    public void after(Object target, Object[] args, Object result, Throwable throwable) {
        if (isDebug) {
            logger.afterInterceptor(target, args);
        }

        final Trace trace = traceContext.currentTraceObject();
        if (trace == null) {
            return;
        }

        try {
            final HttpHost httpHost = ArrayArgumentUtils.getArgument(args, 0, HttpHost.class);
            if (httpHost == null) {
                return;
            }
            final NameIntValuePair<String> host = getHost(httpHost);
            final HttpRequest httpRequest = ArrayArgumentUtils.getArgument(args, 1, HttpRequest.class);
            if (httpRequest == null) {
                return;
            }

            SpanEventRecorder recorder = trace.currentSpanEventRecorder();
            // Accessing httpRequest here not BEFORE() because it can cause side effect.
            ClientRequestWrapper clientRequest = new HttpClient5RequestWrapper(httpRequest, host.getName(), host.getValue());
            this.clientRequestRecorder.record(recorder, clientRequest, throwable);
            this.cookieRecorder.record(recorder, httpRequest, throwable);
            this.entityRecorder.record(recorder, httpRequest, throwable);
            recorder.recordApi(methodDescriptor);
            recorder.recordException(throwable);

            if (result instanceof AsyncContextAccessor) {
                final AsyncContext asyncContext = recorder.recordNextAsyncContext();
                ((AsyncContextAccessor) result)._$PINPOINT$_setAsyncContext(asyncContext);
            }
        } catch (Throwable t) {
            logger.warn("Failed to AFTER process. {}", t.getMessage(), t);
        } finally {
            trace.traceBlockEnd();
        }
    }
}
