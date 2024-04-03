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

package com.navercorp.pinpoint.profiler.instrument.mock;

import com.navercorp.pinpoint.bootstrap.context.MethodDescriptor;
import com.navercorp.pinpoint.bootstrap.context.MethodDescriptorHelper;
import com.navercorp.pinpoint.bootstrap.interceptor.MethodDescriptorAwareAroundInterceptor;

public class MethodDescriptorAwareInterceptorClass implements MethodDescriptorAwareAroundInterceptor {

    public void before() {
        String[] parameterTypes = new String[3];
        parameterTypes[0] = "java.lang.String";
        parameterTypes[1] = "int";
        parameterTypes[2] = "java.lang.Object";


        String[] parameterNames = new String[3];
        parameterNames[0] = "x";
        parameterNames[1] = "y";
        parameterNames[2] = "z";

        MethodDescriptor methodDescriptor = MethodDescriptorHelper.fullName("foo");
        System.out.println(methodDescriptor);
    }

    @Override
    public void before(Object target, MethodDescriptor methodDescriptor, Object[] args) {

    }

    @Override
    public void after(Object target, MethodDescriptor methodDescriptor, Object[] args, Object result, Throwable throwable) {

    }
}
