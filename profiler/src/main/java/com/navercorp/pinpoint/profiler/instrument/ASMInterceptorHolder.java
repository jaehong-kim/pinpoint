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

import com.navercorp.pinpoint.bootstrap.instrument.InstrumentClass;
import com.navercorp.pinpoint.bootstrap.instrument.InstrumentException;
import com.navercorp.pinpoint.bootstrap.instrument.InstrumentMethod;
import com.navercorp.pinpoint.bootstrap.interceptor.Interceptor;
import com.navercorp.pinpoint.profiler.instrument.classloading.InterceptorDefineClassHelper;
import com.navercorp.pinpoint.profiler.instrument.interceptor.InterceptorHolder;
import com.navercorp.pinpoint.profiler.instrument.interceptor.InterceptorLazyLoadingSupplier;
import com.navercorp.pinpoint.profiler.interceptor.factory.AnnotatedInterceptorFactory;
import com.navercorp.pinpoint.profiler.util.JavaAssistUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Supplier;

public class ASMInterceptorHolder {
    private static final String INTERCEPTOR_HOLDER_CLASS = "com/navercorp/pinpoint/profiler/instrument/interceptor/InterceptorHolder.class";
    private static final String INTERCEPTOR_HOLDER_INNER_CLASS = "com/navercorp/pinpoint/profiler/instrument/interceptor/InterceptorHolder$LazyLoading.class";

    public static String getInterceptorHolderClassName(int interceptorId) {
        return InterceptorHolder.class.getName() + "__" + interceptorId;
    }

    private String className;
    private String innerClassName;


    public ASMInterceptorHolder(int interceptorId) {
        this.className = getInterceptorHolderClassName(interceptorId);
        this.innerClassName = this.className + "$LazyLoading";
    }

    public Class<? extends Interceptor> loadInterceptorClass(ClassLoader classLoader) throws InstrumentException {
        try {
            final Class<?> clazz = classLoader.loadClass(className);
            final Method method = clazz.getDeclaredMethod("get");
            final Object o = method.invoke(null);
            if (o instanceof Interceptor) {
                return (Class<? extends Interceptor>) o.getClass();
            } else {
                throw new InstrumentException("not found interceptor, className=" + className);
            }
        } catch (ClassNotFoundException e) {
            throw new InstrumentException("not found class, className=" + className, e);
        } catch (InvocationTargetException e) {
            throw new InstrumentException("invocation fail, className=" + className, e);
        } catch (NoSuchMethodException e) {
            throw new InstrumentException("not found 'get' method, className=" + className, e);
        } catch (IllegalAccessException e) {
            throw new InstrumentException("access fail, className=" + className, e);
        }
    }

    public void init(Class<?> interceptorHolderClass, AnnotatedInterceptorFactory factory, Class<?> interceptorClass, Object[] providedArguments, ScopeInfo scopeInfo, InstrumentClass targetClass, InstrumentMethod targetMethod) throws InstrumentException {
        try {
            final InterceptorLazyLoadingSupplier interceptorLazyLoadingSupplier = new InterceptorLazyLoadingSupplier(factory, interceptorClass, providedArguments, scopeInfo, targetClass, targetMethod);
            final Method method = interceptorHolderClass.getDeclaredMethod("set", Supplier.class);
            method.invoke(null, interceptorLazyLoadingSupplier);
        } catch (NoSuchMethodException e) {
            throw new InstrumentException("not found 'set' method, class=" + interceptorHolderClass.getName(), e);
        } catch (IllegalAccessException e) {
            throw new InstrumentException("access fail, class=" + interceptorHolderClass.getName(), e);
        } catch (InvocationTargetException e) {
            throw new InstrumentException("invocation fail, class=" + interceptorHolderClass.getName(), e);
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
