/*
 * Copyright 2017 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.navercorp.pinpoint.profiler.instrument;

import com.navercorp.pinpoint.bootstrap.interceptor.scope.ExecutionPolicy;
import com.navercorp.pinpoint.bootstrap.interceptor.scope.InterceptorScope;

/**
 * @author Woonduk Kang(emeroad)
 */
public class ScopeInfo {
    private final InterceptorScope interceptorScope;
    private final ExecutionPolicy executionPolicy;

    public ScopeInfo(InterceptorScope interceptorScope, ExecutionPolicy executionPolicy) {
        this.interceptorScope = interceptorScope;
        this.executionPolicy = executionPolicy;
    }

    public InterceptorScope getInterceptorScope() {
        return interceptorScope;
    }

    public ExecutionPolicy getExecutionPolicy() {
        return executionPolicy;
    }

    public String getId() {
        StringBuilder builder = new StringBuilder();
        if (interceptorScope != null) {
            builder.append(interceptorScope.getClass().getName());
            builder.append(Integer.toHexString(interceptorScope.hashCode()));
        }
        if (executionPolicy != null) {
            builder.append(executionPolicy.name());
        }
        return builder.toString();
    }

    @Override
    public String toString() {
        return "ScopeInfo{" +
                "interceptorScope=" + interceptorScope +
                ", executionPolicy=" + executionPolicy +
                '}';
    }
}
