/*
 * Copyright 2018 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.navercorp.pinpoint.plugin.jboss.interceptor;

import com.navercorp.pinpoint.bootstrap.context.MethodDescriptor;
import com.navercorp.pinpoint.bootstrap.context.SpanEventRecorder;
import com.navercorp.pinpoint.bootstrap.context.Trace;
import com.navercorp.pinpoint.bootstrap.context.TraceContext;
import com.navercorp.pinpoint.bootstrap.interceptor.AroundInterceptor;
import com.navercorp.pinpoint.bootstrap.interceptor.SpanEventSimpleAroundInterceptorForPlugin;
import com.navercorp.pinpoint.bootstrap.logging.PLogger;
import com.navercorp.pinpoint.bootstrap.logging.PLoggerFactory;
import com.navercorp.pinpoint.plugin.jboss.JbossAsyncListener;
import com.navercorp.pinpoint.plugin.jboss.JbossConstants;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncListener;
import javax.servlet.http.HttpServletRequest;
import java.util.Objects;

/**
 * @author jaehong.kim
 */
public class RequestStartAsyncInterceptor implements AroundInterceptor {
    private final PLogger logger = PLoggerFactory.getLogger(getClass());
    private final boolean isDebug = logger.isDebugEnabled();

    private final MethodDescriptor methodDescriptor;
    private final TraceContext traceContext;

    public RequestStartAsyncInterceptor(TraceContext traceContext, MethodDescriptor methodDescriptor) {
        this.traceContext = Objects.requireNonNull(traceContext, "traceContext");
        this.methodDescriptor = Objects.requireNonNull(methodDescriptor, "methodDescriptor");
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
            trace.traceBlockBegin();
        } catch (Throwable th) {
            if (logger.isWarnEnabled()) {
                logger.warn("BEFORE. Caused:{}", th.getMessage(), th);
            }
        }
    }

    @Override
    public void after(Object target, Object[] args, Object result, Throwable throwable) {
        if (isDebug) {
            logger.afterInterceptor(target, args, result, throwable);
        }

        final Trace trace = traceContext.currentRawTraceObject();
        if (trace == null) {
            return;
        }
        try {
            final SpanEventRecorder recorder = trace.currentSpanEventRecorder();
            if (validate(target, result, throwable)) {
                // Add async listener. Servlet 3.0
                final AsyncContext asyncContext = (AsyncContext) result;
                final AsyncListener asyncListener = new JbossAsyncListener(this.traceContext, recorder.recordNextAsyncContext(true));
                asyncContext.addListener(asyncListener);
                if (isDebug) {
                    logger.debug("Add async listener {}", asyncListener);
                }
            }

            recorder.recordServiceType(JbossConstants.JBOSS_METHOD);
            recorder.recordApi(methodDescriptor);
            recorder.recordException(throwable);
        } catch (Throwable th) {
            if (logger.isWarnEnabled()) {
                logger.warn("AFTER error. Caused:{}", th.getMessage(), th);
            }
        } finally {
            trace.traceBlockEnd();
        }
    }

    private boolean validate(final Object target, final Object result, final Throwable throwable) {
        if (throwable != null || result == null) {
            return false;
        }

        if (!(target instanceof HttpServletRequest)) {
            if (isDebug) {
                logger.debug("Invalid target object, The javax.servlet.http.HttpServletRequest interface is not implemented. target={}", target);
            }
            return false;
        }
        if (!(result instanceof AsyncContext)) {
            if (isDebug) {
                logger.debug("Invalid result object, The javax.servlet.AsyncContext interface is not implemented. result={}.", result);
            }
            return false;
        }
        return true;
    }

}