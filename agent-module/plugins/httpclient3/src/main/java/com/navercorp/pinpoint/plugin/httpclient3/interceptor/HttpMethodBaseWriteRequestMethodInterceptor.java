/*
 * Copyright 2014 NAVER Corp.
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

package com.navercorp.pinpoint.plugin.httpclient3.interceptor;

import com.navercorp.pinpoint.bootstrap.context.MethodDescriptor;
import com.navercorp.pinpoint.bootstrap.context.TraceContext;
import com.navercorp.pinpoint.bootstrap.interceptor.scope.InterceptorScope;
import com.navercorp.pinpoint.plugin.httpclient3.HttpClient3CallContext;

/**
 * @author jaehong.kim
 */
public class HttpMethodBaseWriteRequestMethodInterceptor extends HttpMethodBaseRequestAndResponseMethodInterceptor {

    public HttpMethodBaseWriteRequestMethodInterceptor(TraceContext traceContext, MethodDescriptor methodDescriptor, InterceptorScope interceptorScope) {
        super(traceContext, methodDescriptor, interceptorScope);
    }

    @Override
    void recordBegin(HttpClient3CallContext callContext) {
        callContext.setWriteBeginTime(System.currentTimeMillis());
    }

    @Override
    void recordEnd(HttpClient3CallContext callContext, Throwable throwable) {
        callContext.setWriteEndTime(System.currentTimeMillis());
        callContext.setWriteFail(throwable != null);
    }
}
