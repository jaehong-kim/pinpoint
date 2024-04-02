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
public class ScopeFactory {

    public ScopeFactory() {
    }

    public ScopeInfo newScopeInfo(InterceptorScope scope, ExecutionPolicy policy) {
        policy = getExecutionPolicy(scope, policy);
        return new ScopeInfo(scope, policy);
    }

    private ExecutionPolicy getExecutionPolicy(InterceptorScope scope, ExecutionPolicy policy) {
        if (scope == null) {
            policy = null;
        } else if (policy == null) {
            policy = ExecutionPolicy.BOUNDARY;
        }
        return policy;
    }
}
