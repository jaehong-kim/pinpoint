/*
 * Copyright 2023 NAVER Corp.
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

package com.navercorp.pinpoint.profiler.instrument;

import com.navercorp.pinpoint.bootstrap.context.MethodDescriptor;
import com.navercorp.pinpoint.bootstrap.instrument.InstrumentException;
import com.navercorp.pinpoint.bootstrap.interceptor.Interceptor;
import com.navercorp.pinpoint.profiler.instrument.classloading.InterceptorDefineClassHelper;
import com.navercorp.pinpoint.profiler.instrument.interceptor.InterceptorDefinition;
import com.navercorp.pinpoint.profiler.instrument.interceptor.InterceptorLazyLoadingSupplier;
import com.navercorp.pinpoint.profiler.instrument.interceptor.InterceptorSupplier;
import com.navercorp.pinpoint.profiler.interceptor.factory.InterceptorFactory;
import com.navercorp.pinpoint.profiler.util.JavaAssistUtils;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class ASMInterceptorHolder {
    private static final String INTERCEPTOR_HOLDER_CLASS = "com/navercorp/pinpoint/profiler/instrument/interceptor/InterceptorHolder.class";
    private static final String INTERCEPTOR_HOLDER_INNER_CLASS = "com/navercorp/pinpoint/profiler/instrument/interceptor/InterceptorHolder$LazyLoading.class";

    private final MethodDescriptor methodDescriptor;
    private final InterceptorDefinition interceptorDefinition;
    private final Object[] constructorArgs;
    private final ScopeInfo scopeInfo;
    private final String className;
    private final String innerClassName;
    private final AtomicInteger id = new AtomicInteger(0);
    private boolean reusable = false;

    public ASMInterceptorHolder(MethodDescriptor methodDescriptor, InterceptorDefinition interceptorDefinition, Object[] constructorArgs, ScopeInfo scopeInfo) {
        this.methodDescriptor = methodDescriptor;
        this.interceptorDefinition = interceptorDefinition;
        this.constructorArgs = constructorArgs;
        this.scopeInfo = scopeInfo;

        this.className = toClassName();
        this.innerClassName = this.className + "$LazyLoading";
    }

    private String toClassName() {
        // interceptorClassName
        final StringBuilder builder = new StringBuilder(interceptorDefinition.getInterceptorClass().getName());
        if (constructorArgs != null) {
            builder.append("$$");
            builder.append(id.getAndIncrement());
            return builder.toString();
        }
        if (scopeInfo != null) {
            builder.append("$$");
            builder.append(scopeInfo.getId());
        }
        return builder.toString();
    }

    private boolean isResuable(Class<? extends Interceptor> interceptorClass) {
        final Constructor<?>[] constructors = type.getConstructors();
        for (Constructor<?> constructor : constructors) {
            final Class<?>[] parameterTypes = constructor.getParameterTypes();


            Object[] resolvedArguments = argumentsResolver.resolve(parameterTypes);

            if (resolvedArguments != null) {
                this.resolvedConstructor = constructor;
                this.resolvedArguments = resolvedArguments;

                return true;
            }
        }

    }


    public String getClassName() {
        return className;
    }

    public Class<? extends Interceptor> loadInterceptorClass(ClassLoader classLoader) {
        try {
            final Class<?> clazz = classLoader.loadClass(className);
            final Method method = clazz.getDeclaredMethod("get");
            final Object o = method.invoke(null);
            if (o instanceof Interceptor) {
                return (Class<? extends Interceptor>) o.getClass();
            }
        } catch (Exception ignored) {
            // ClassNotFoundException
            // InvocationTargetException
            // NoSuchMethodException
            // IllegalAccessException
        }
        return null;
    }

    public void init(Class<?> interceptorHolderClass, InterceptorFactory factory, MethodDescriptor methodDescriptor) throws InstrumentException {
        init(interceptorHolderClass, new InterceptorLazyLoadingSupplier(factory, interceptorDefinition.getInterceptorClass(), constructorArgs, scopeInfo, methodDescriptor));
    }

    public void init(Class<?> interceptorHolderClass, Interceptor interceptor) throws InstrumentException {
        init(interceptorHolderClass, new InterceptorSupplier(interceptor));
    }

    private void init(Class<?> interceptorHolderClass, Supplier<Interceptor> supplier) throws InstrumentException {
        try {
            final Method method = interceptorHolderClass.getDeclaredMethod("set", Supplier.class);
            method.invoke(null, supplier);
        } catch (NoSuchMethodException e) {
            throw new InstrumentException("not found 'set' method, className=" + interceptorHolderClass.getName(), e);
        } catch (IllegalAccessException e) {
            throw new InstrumentException("access fail, className=" + interceptorHolderClass.getName(), e);
        } catch (InvocationTargetException e) {
            throw new InstrumentException("invocation fail, className=" + interceptorHolderClass.getName(), e);
        }
    }


    public Class<?> defineClass(ClassLoader classLoader) throws InstrumentException {
        final byte[] mainClassBytes = toMainClassByteArray();
        final Class<?> mainClass = InterceptorDefineClassHelper.defineClass(classLoader, className, mainClassBytes);
        final byte[] innerClassByte = toInnerClassByteArray();
        InterceptorDefineClassHelper.defineClass(classLoader, innerClassName, innerClassByte);
        return mainClass;
    }

    byte[] toMainClassByteArray() throws InstrumentException {
        try {
            ClassNode classNode = readClass(INTERCEPTOR_HOLDER_CLASS);
            final ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            final ClassVisitor renameClassAdapter = new RenameClassAdapter(classWriter, className);
            classNode.accept(renameClassAdapter);
            return classWriter.toByteArray();
        } catch (IOException e) {
            // read fail
            throw new InstrumentException("ClassReader fail, classFile=" + INTERCEPTOR_HOLDER_CLASS);
        }
    }

    byte[] toInnerClassByteArray() throws InstrumentException {
        try {
            ClassNode classNode = readClass(INTERCEPTOR_HOLDER_INNER_CLASS);
            final ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            final ClassVisitor renameClassAdapter = new RenameInnerClassAdapter(classWriter, className);
            classNode.accept(renameClassAdapter);
            return classWriter.toByteArray();
        } catch (IOException e) {
            // read fail
            throw new InstrumentException("ClassReader fail, classFile=" + INTERCEPTOR_HOLDER_INNER_CLASS);
        }
    }

    ClassNode readClass(String classFileName) throws IOException {
        final ClassLoader classLoader = ASMInterceptorHolder.class.getClassLoader();
        final ClassReader classReader = new ClassReader(classLoader.getResourceAsStream(classFileName));
        ClassNode classNode = new ClassNode();
        classReader.accept(classNode, 0);
        return classNode;
    }

    public static String create(ClassLoader classLoader, MethodDescriptor methodDescriptor, InterceptorDefinition interceptorDefinition, Object[] constructorArgs, ScopeInfo scopeInfo, InterceptorFactory interceptorFactory) throws InstrumentException {
        Builder builder = new Builder(classLoader, methodDescriptor, interceptorDefinition, constructorArgs, scopeInfo);
        builder.interceptorFactory(interceptorFactory);
        return builder.build();
    }

    public static String create(ClassLoader classLoader, MethodDescriptor methodDescriptor, InterceptorDefinition interceptorDefinition, Object[] constructorArgs, ScopeInfo scopeInfo, Interceptor interceptor) throws InstrumentException {
        Builder builder = new Builder(classLoader, methodDescriptor, interceptorDefinition, constructorArgs, scopeInfo);
        builder.interceptor(interceptor);
        return builder.build();
    }

    static class Builder {

        static AtomicInteger createCounter = new AtomicInteger();
        static AtomicInteger findCounter = new AtomicInteger();

        private final ClassLoader classLoader;
        private final MethodDescriptor methodDescriptor;
        private InterceptorDefinition interceptorDefinition;
        private final Object[] constructorArgs;
        private final ScopeInfo scopeInfo;
        private InterceptorFactory interceptorFactory;

        private Interceptor interceptor;

        public Builder(ClassLoader classLoader, MethodDescriptor methodDescriptor, InterceptorDefinition interceptorDefinition, Object[] constructorArgs, ScopeInfo scopeInfo) {
            this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
            this.methodDescriptor = Objects.requireNonNull(methodDescriptor, "methodDescriptor");
            this.interceptorDefinition = Objects.requireNonNull(interceptorDefinition, "interceptorDefinition");
            this.constructorArgs = constructorArgs;
            this.scopeInfo = scopeInfo;
        }

        public Builder interceptorFactory(InterceptorFactory interceptorFactory) {
            this.interceptorFactory = interceptorFactory;
            return this;
        }

        public Builder interceptor(Interceptor interceptor) {
            this.interceptor = interceptor;
            return this;
        }

        public String build() throws InstrumentException {
            int totalCount = createCounter.incrementAndGet();
            final ASMInterceptorHolder holder = new ASMInterceptorHolder(methodDescriptor, interceptorDefinition, constructorArgs, scopeInfo);
            final Class<? extends Interceptor> interceptorHolderClass = holder.loadInterceptorClass(classLoader);
            if (interceptorHolderClass != null) {
                int skipCount = findCounter.incrementAndGet();
                System.out.println("## FIND=" + holder.getClassName() + "---- total=" + totalCount + ", skip=" + skipCount);
                return holder.getClassName();
            }

            final Class<?> clazz = holder.defineClass(classLoader);
            // exception handling.
            if (interceptor != null) {
                holder.init(clazz, interceptor);
            } else if (interceptorFactory != null) {
                holder.init(clazz, interceptorFactory, methodDescriptor);
            } else {
                throw new InstrumentException("Either interceptor or interceptorFactory must be present.");
            }

            System.out.println("## INIT=" + holder.getClassName() + "---- total=" + totalCount);
            return holder.getClassName();
        }
    }

    class RenameClassAdapter extends ClassVisitor {
        private final String newInternalName;

        public RenameClassAdapter(ClassVisitor classVisitor, String name) {
            super(ASMVersion.VERSION, classVisitor);
            this.newInternalName = JavaAssistUtils.javaNameToJvmName(name);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, this.newInternalName, signature, superName, interfaces);
        }

        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            super.visitInnerClass(newInternalName + "$" + innerName, newInternalName, innerName, access);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            if (name.equals("<init>")) {
                return new MethodVisitor(api, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override
                    public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
                        // LOCALVARIABLE this Lcom/navercorp/pinpoint/profiler/instrument/mock/InterceptorHolder; L0 L1 0
                        if (name.equals("this")) {
                            final String newDescriptor = "L" + newInternalName + ";";
                            super.visitLocalVariable(name, newDescriptor, signature, start, end, index);
                        } else {
                            super.visitLocalVariable(name, descriptor, signature, start, end, index);
                        }
                    }
                };
            } else if (name.equals("get")) {
                return new MethodVisitor(api, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override
                    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                        // GETSTATIC com/navercorp/pinpoint/profiler/instrument/mock/InterceptorHolder.holder : Lcom/navercorp/pinpoint/bootstrap/interceptor/Interceptor;
                        final int innerNameStartPosition = owner.indexOf('$');
                        if (innerNameStartPosition != -1) {
                            final String innerName = owner.substring(innerNameStartPosition);
                            super.visitFieldInsn(opcode, newInternalName + innerName, name, descriptor);
                        } else {
                            super.visitFieldInsn(opcode, newInternalName, name, descriptor);
                        }
                    }
                };
            } else if (name.equals("set")) {
                return new MethodVisitor(api, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override
                    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                        // PUTSTATIC com/navercorp/pinpoint/profiler/instrument/mock/InterceptorBinder.binder : Lcom/navercorp/pinpoint/bootstrap/interceptor/Interceptor;
                        super.visitFieldInsn(opcode, newInternalName, name, descriptor);
                    }
                };
            } else if (name.equals("access$000")) {
                return new MethodVisitor(api, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override
                    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                        // GETSTATIC com/navercorp/pinpoint/profiler/instrument/mock/InterceptorBinder.factory : Lcom/navercorp/pinpoint/profiler/instrument/mock/InterceptorLazyFactory;
                        super.visitFieldInsn(opcode, newInternalName, name, descriptor);
                    }
                };
            }

            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }
    }

    class RenameInnerClassAdapter extends ClassVisitor {
        private final String newInternalName;

        public RenameInnerClassAdapter(ClassVisitor classVisitor, String name) {
            super(ASMVersion.VERSION, classVisitor);
            this.newInternalName = JavaAssistUtils.javaNameToJvmName(name);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            final int innerNameStartPosition = name.indexOf('$');
            if (innerNameStartPosition != -1) {
                final String innerName = name.substring(innerNameStartPosition);
                super.visit(version, access, this.newInternalName + innerName, signature, superName, interfaces);
            } else {
                super.visit(version, access, this.newInternalName, signature, superName, interfaces);
            }
        }

        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            super.visitInnerClass(newInternalName + "$" + innerName, newInternalName, innerName, access);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            if (name.equals("<init>")) {
                return new MethodVisitor(api, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override
                    public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
                        // LOCALVARIABLE this Lcom/navercorp/pinpoint/profiler/instrument/mock/InterceptorHolder; L0 L1 0
                        if (name.equals("this")) {
                            final int innerNameStartPosition = descriptor.indexOf('$');
                            if (innerNameStartPosition != -1) {
                                final String innerName = descriptor.substring(innerNameStartPosition);
                                final String newDescriptor = "L" + newInternalName + innerName;
                                super.visitLocalVariable(name, newDescriptor, signature, start, end, index);
                            } else {
                                super.visitLocalVariable(name, descriptor, signature, start, end, index);
                            }
                        } else {
                            super.visitLocalVariable(name, descriptor, signature, start, end, index);
                        }
                    }
                };
            } else if (name.equals("<clinit>")) {
                return new MethodVisitor(api, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                        if (name.equals("access$000")) {
                            super.visitMethodInsn(opcode, newInternalName, name, descriptor, isInterface);
                        } else {
                            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                        }
                    }

                    @Override
                    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                        // GETSTATIC com/navercorp/pinpoint/profiler/instrument/mock/InterceptorHolder.holder : Lcom/navercorp/pinpoint/bootstrap/interceptor/Interceptor;
                        final int innerNameStartPosition = owner.indexOf('$');
                        if (innerNameStartPosition != -1) {
                            final String innerName = owner.substring(innerNameStartPosition);
                            super.visitFieldInsn(opcode, newInternalName + innerName, name, descriptor);
                        } else {
                            super.visitFieldInsn(opcode, newInternalName, name, descriptor);
                        }
                    }
                };
            }

            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }
    }
}
