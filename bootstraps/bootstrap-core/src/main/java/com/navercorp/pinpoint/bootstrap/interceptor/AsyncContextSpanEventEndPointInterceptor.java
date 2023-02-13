/*
 * Copyright 2019 NAVER Corp.
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

package com.navercorp.pinpoint.bootstrap.interceptor;

import com.navercorp.pinpoint.bootstrap.async.AsyncContextAccessorUtils;
import com.navercorp.pinpoint.bootstrap.context.AsyncContext;
import com.navercorp.pinpoint.bootstrap.context.AsyncContextUtils;
import com.navercorp.pinpoint.bootstrap.context.MethodDescriptor;
import com.navercorp.pinpoint.bootstrap.context.SpanEventRecorder;
import com.navercorp.pinpoint.bootstrap.context.Trace;
import com.navercorp.pinpoint.bootstrap.context.TraceContext;
import com.navercorp.pinpoint.bootstrap.logging.PLogger;
import com.navercorp.pinpoint.bootstrap.logging.PLoggerFactory;
import com.navercorp.pinpoint.bootstrap.util.ScopeUtils;

import java.util.Objects;

/**
 * @author jaehong.kim
 */
public abstract class AsyncContextSpanEventEndPointInterceptor implements AroundInterceptor {
    protected final PLogger logger = PLoggerFactory.getLogger(getClass());
    protected final boolean isDebug = logger.isDebugEnabled();
    protected static final String ASYNC_TRACE_SCOPE = AsyncContext.ASYNC_TRACE_SCOPE;

    protected final MethodDescriptor methodDescriptor;
    protected final TraceContext traceContext;

    public AsyncContextSpanEventEndPointInterceptor(TraceContext traceContext, MethodDescriptor methodDescriptor) {
        this.traceContext = Objects.requireNonNull(traceContext, "traceContext");
        this.methodDescriptor = Objects.requireNonNull(methodDescriptor, "methodDescriptor");
    }

    @Override
    public void before(Object target, Object[] args) {
        if (isDebug) {
            logger.beforeInterceptor(target, args);
        }

        final AsyncContext asyncContext = getAsyncContext(target, args);
        if (asyncContext == null) {
            logger.debug("AsyncContext not found");
            return;
        }

        final Trace trace = getAsyncTrace(asyncContext);
        if (trace == null) {
            return;
        }

        if (isDebug) {
            logger.debug("Asynchronous invocation. asyncTraceId={}, trace={}", asyncContext, trace);
        }
        // entry scope.
        ScopeUtils.entryAsyncTraceScope(trace);

        try {
            // trace event for default & async.
            final SpanEventRecorder recorder = trace.traceBlockBegin();
            doInBeforeTrace(recorder, asyncContext, target, args);
        } catch (Throwable th) {
            if (logger.isWarnEnabled()) {
                logger.warn("BEFORE. Caused:{}", th.getMessage(), th);
            }
        }
    }

    // Trace trace, AsyncContext asyncContext
    protected void doInBeforeTrace(Trace trace, AsyncContext asyncContext, SpanEventRecorder recorder, Object target, Object[] args) {
        doInBeforeTrace(asyncContext, recorder, target, args);
    }

    // AsyncContext asyncContext
    protected void doInBeforeTrace(AsyncContext asyncContext, SpanEventRecorder recorder, Object target, Object[] args) {
        doInBeforeTrace(recorder, asyncContext, target, args);
    }

    protected abstract void doInBeforeTrace(SpanEventRecorder recorder, AsyncContext asyncContext, Object target, Object[] args);

    @Override
    public void after(Object target, Object[] args, Object result, Throwable throwable) {
        if (isDebug) {
            logger.afterInterceptor(target, args, result, throwable);
        }

        final AsyncContext asyncContext = getAsyncContext(target, args, result, throwable);
        if (asyncContext == null) {
            if (isDebug) {
                logger.debug("Not found asynchronous invocation metadata");
            }
            return;
        }
        if (isDebug) {
            logger.debug("Asynchronous invocation. asyncContext={}", asyncContext);
        }

        final Trace trace = asyncContext.currentAsyncRawTraceObject();
        if (trace == null) {
            return;
        }
        if (isDebug) {
            logger.debug("Asynchronous invocation. asyncTraceId={}, trace={}", asyncContext, trace);
        }

        // leave scope.
        if (!ScopeUtils.leaveAsyncTraceScope(trace)) {
            if (logger.isWarnEnabled()) {
                logger.warn("Failed to leave scope of async trace {}.", trace);
            }
            // delete unstable trace.
            deleteAsyncTrace(trace);
            return;
        }

        try {
            final SpanEventRecorder recorder = trace.currentSpanEventRecorder();
            doInAfterTrace(trace, asyncContext, recorder, target, args, result, throwable);
        } catch (Throwable th) {
            if (logger.isWarnEnabled()) {
                logger.warn("AFTER error. Caused:{}", th.getMessage(), th);
            }
        } finally {
            trace.traceBlockEnd();
            if (ScopeUtils.isAsyncTraceEndScope(trace)) {
                if (isDebug) {
                    logger.debug("Arrived at async trace destination. asyncTraceId={}", asyncContext);
                }
                deleteAsyncTrace(trace);
            }
            finishAsyncState(asyncContext);
        }
    }

    // Trace trace, AsyncContext asyncContext
    protected void doInAfterTrace(Trace trace, AsyncContext asyncContext, SpanEventRecorder recorder, Object target, Object[] args, Object result, Throwable throwable) {
        doInAfterTrace(asyncContext, recorder, target, args, result, throwable);
    }

    // AsyncContext asyncContext
    protected void doInAfterTrace(AsyncContext asyncContext, SpanEventRecorder recorder, Object target, Object[] args, Object result, Throwable throwable) {
        doInAfterTrace(recorder, target, args, result, throwable);
    }

    protected abstract void doInAfterTrace(SpanEventRecorder recorder, Object target, Object[] args, Object result, Throwable throwable);

    protected AsyncContext getAsyncContext(Object target, Object[] args) {
        return AsyncContextAccessorUtils.getAsyncContext(target);
    }

    protected AsyncContext getAsyncContext(Object target, Object[] args, Object result, Throwable throwable) {
        return AsyncContextAccessorUtils.getAsyncContext(target);
    }

    private Trace getAsyncTrace(AsyncContext asyncContext) {
        final Trace trace = asyncContext.continueAsyncRawTraceObject();
        if (trace == null) {
            if (isDebug) {
                logger.debug("Failed to continue async trace. 'result is null'");
            }
            return null;
        }
        if (isDebug) {
            logger.debug("getAsyncTrace() trace {}, asyncContext={}", trace, asyncContext);
        }

        return trace;
    }

    private void deleteAsyncTrace(final Trace trace) {
        if (isDebug) {
            logger.debug("Delete async trace {}.", trace);
        }
        traceContext.removeTraceObject();
        trace.close();
    }


    private void finishAsyncState(final AsyncContext asyncContext) {
        if (AsyncContextUtils.asyncStateFinish(asyncContext)) {
            if (isDebug) {
                logger.debug("finished asyncState. asyncTraceId={}", asyncContext);
            }
        }
    }
}