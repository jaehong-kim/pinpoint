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

package com.navercorp.pinpoint.bootstrap.context;

import com.navercorp.pinpoint.common.trace.MethodType;

import java.util.Arrays;

public class MethodDescriptorFactory {

    public static MethodDescriptor get(String className, String methodName, String[] parameterTypes, String[] parameterVariableName, int lineNumber) {
        return new DefaultMethodDescriptor(className, methodName, parameterTypes, parameterVariableName, lineNumber);
    }

    static class DefaultMethodDescriptor implements MethodDescriptor {
        private static final int TYPE = MethodType.DEFAULT;
        private final static String EMPTY_ARRAY = "()";

        private final String className;

        private final String methodName;

        private final String[] parameterTypes;

        private final String[] parameterVariableName;

        private final String parameterDescriptor;

        private final String apiDescriptor;

        private final int lineNumber;

        private int apiId = 0;

        private String fullName;

        public DefaultMethodDescriptor(String className, String methodName, String[] parameterTypes, String[] parameterVariableName, int lineNumber) {
            this.className = className;
            this.methodName = methodName;
            this.parameterTypes = parameterTypes;
            this.parameterVariableName = parameterVariableName;
            this.parameterDescriptor = mergeParameterVariableNameDescription(parameterTypes, parameterVariableName);
            this.apiDescriptor = mergeApiDescriptor(className, methodName, parameterDescriptor);
            this.lineNumber = lineNumber;
        }

        public String getParameterDescriptor() {
            return parameterDescriptor;
        }


        @Override
        public String getMethodName() {
            return methodName;
        }

        @Override
        public String getClassName() {
            return className;
        }


        @Override
        public String[] getParameterTypes() {
            return parameterTypes;
        }

        @Override
        public String[] getParameterVariableName() {
            return parameterVariableName;
        }


        public int getLineNumber() {
            return lineNumber;
        }

        @Override
        public String getFullName() {
            if (fullName != null) {
                return fullName;
            }
            StringBuilder buffer = new StringBuilder(256);
            buffer.append(className);
            buffer.append('.');
            buffer.append(methodName);
            buffer.append(parameterDescriptor);
            if (lineNumber != -1) {
                buffer.append(':');
                buffer.append(lineNumber);
            }
            fullName = buffer.toString();
            return fullName;
        }


        @Override
        public String getApiDescriptor() {
            return apiDescriptor;
        }

        @Override
        public void setApiId(int apiId) {
            this.apiId = apiId;
        }

        @Override
        public int getApiId() {
            return apiId;
        }

        public int getType() {
            return TYPE;
        }

        String mergeParameterVariableNameDescription(String[] parameterType, String[] variableName) {
            if (parameterType == null && variableName == null) {
                return EMPTY_ARRAY;
            }
            if (variableName != null && parameterType != null) {
                if (parameterType.length != variableName.length) {
                    throw new IllegalArgumentException("args size not equal");
                }
                if (parameterType.length == 0) {
                    return EMPTY_ARRAY;
                }
                StringBuilder sb = new StringBuilder(64);
                sb.append('(');
                int end = parameterType.length - 1;
                for (int i = 0; i < parameterType.length; i++) {
                    sb.append(parameterType[i]);
                    sb.append(' ');
                    sb.append(variableName[i]);
                    if (i < end) {
                        sb.append(", ");
                    }
                }
                sb.append(')');
                return sb.toString();
            }
            throw new IllegalArgumentException("invalid null pair parameterType:" + Arrays.toString(parameterType) + ", variableName:" + Arrays.toString(variableName));
        }

        String mergeApiDescriptor(String className, String methodName, String parameterDescriptor) {
            StringBuilder buffer = new StringBuilder(256);
            buffer.append(className);
            buffer.append('.');
            buffer.append(methodName);
            buffer.append(parameterDescriptor);
            return buffer.toString();
        }
    }
}
