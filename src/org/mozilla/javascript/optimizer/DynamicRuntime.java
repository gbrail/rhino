/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.optimizer;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import jdk.dynalink.CallSiteDescriptor;
import jdk.dynalink.DynamicLinker;
import jdk.dynalink.DynamicLinkerFactory;
import jdk.dynalink.NamedOperation;
import jdk.dynalink.NamespaceOperation;
import jdk.dynalink.Operation;
import jdk.dynalink.StandardNamespace;
import jdk.dynalink.StandardOperation;
import jdk.dynalink.linker.GuardedInvocation;
import jdk.dynalink.linker.GuardingDynamicLinker;
import jdk.dynalink.linker.LinkRequest;
import jdk.dynalink.linker.LinkerServices;
import jdk.dynalink.support.SimpleRelinkableCallSite;
import org.mozilla.classfile.ByteCode;
import org.mozilla.classfile.ClassFileWriter;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.SlotMap;

public class DynamicRuntime {
    public enum RhinoOperation implements Operation {
        GETNOWARN,
    };

    public static final String BOOTSTRAP_SIGNATURE =
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;";

    public static final String GET_PROP_SIGNATURE =
            "(Ljava/lang/Object;Lorg/mozilla/javascript/Context;Lorg/mozilla/javascript/Scriptable;)Ljava/lang/Object;";
    public static final String SET_PROP_SIGNATURE =
            "(Ljava/lang/Object;Ljava/lang/Object;Lorg/mozilla/javascript/Context;Lorg/mozilla/javascript/Scriptable;)Ljava/lang/Object;";

    public static final ClassFileWriter.MHandle PROP_BOOTSTRAP_HANDLE =
            new ClassFileWriter.MHandle(
                    ByteCode.MH_INVOKESTATIC,
                    "org.mozilla.javascript.optimizer.DynamicRuntime",
                    "bootstrap",
                    DynamicRuntime.BOOTSTRAP_SIGNATURE);

    private static final DynamicLinker LINKER;

    static {
        DynamicLinkerFactory linkerFactory = new DynamicLinkerFactory();
        linkerFactory.setPrioritizedLinkers(new FastLinker(), new FallbackLinker());
        LINKER = linkerFactory.createLinker();
    }

    @SuppressWarnings("unused")
    public static CallSite bootstrap(MethodHandles.Lookup lookup, String name, MethodType mType)
            throws NoSuchMethodException, IllegalAccessException {
        return LINKER.link(
                new SimpleRelinkableCallSite(
                        new CallSiteDescriptor(lookup, parseOperation(name), mType)));
    }

    private static Operation parseOperation(String name) throws NoSuchMethodException {
        if (name.startsWith("GET:")) {
            String propertyName = name.substring(4).intern();
            return StandardOperation.GET
                    .withNamespace(StandardNamespace.PROPERTY)
                    .named(propertyName);
        } else if (name.startsWith("GETNOWARN:")) {
            String propertyName = name.substring(10).intern();
            return RhinoOperation.GETNOWARN
                    .withNamespace(StandardNamespace.PROPERTY)
                    .named(propertyName);
        } else if (name.startsWith("SET:")) {
            String propertyName = name.substring(4).intern();
            return StandardOperation.SET
                    .withNamespace(StandardNamespace.PROPERTY)
                    .named(propertyName);
        } else {
            throw new NoSuchMethodException(name);
        }
    }

    static class FastLinker implements GuardingDynamicLinker {
        @Override
        public GuardedInvocation getGuardedInvocation(LinkRequest req, LinkerServices svcs)
                throws NoSuchMethodException, IllegalAccessException {
            Operation namedOp = req.getCallSiteDescriptor().getOperation();
            if (!(namedOp instanceof NamedOperation)) {
                throw new UnsupportedOperationException("Only named operations supported");
            }
            String propertyName = (String) ((NamedOperation) namedOp).getName();

            Operation nsOp = ((NamedOperation) namedOp).getBaseOperation();
            if (!(nsOp instanceof NamespaceOperation)) {
                throw new UnsupportedOperationException("Only namespace operations supported");
            }
            if (((NamespaceOperation) nsOp).getNamespace(0) != StandardNamespace.PROPERTY) {
                throw new UnsupportedOperationException(
                        "Only property namespace operations supported");
            }

            MethodHandles.Lookup lookup = req.getCallSiteDescriptor().getLookup();
            Operation op = ((NamespaceOperation) nsOp).getBaseOperation();

            if (op == StandardOperation.GET && req.getReceiver() instanceof ScriptableObject) {
                ScriptableObject so = (ScriptableObject) req.getReceiver();
                SlotMap.FastKey key = so.getFastKey(propertyName);
                if (key != null) {
                    MethodType guardType =
                            MethodType.methodType(
                                    Boolean.TYPE,
                                    SlotMap.FastKey.class,
                                    Object.class,
                                    Context.class,
                                    Scriptable.class);
                    MethodHandle rawGuard =
                            lookup.findStatic(DynamicRuntime.class, "guardFastKey", guardType);
                    MethodHandle guard = MethodHandles.insertArguments(rawGuard, 0, key);

                    MethodType invokeType =
                            MethodType.methodType(
                                    Object.class,
                                    SlotMap.FastKey.class,
                                    Object.class,
                                    Context.class,
                                    Scriptable.class);
                    MethodHandle rawInvoke =
                            lookup.findStatic(DynamicRuntime.class, "invokeFastKey", invokeType);
                    MethodHandle invoke = MethodHandles.insertArguments(rawInvoke, 0, key);

                    return new GuardedInvocation(invoke, guard);
                }
            }

            // Let another linker pick this up
            return null;
        }
    }

    public static boolean guardFastKey(
            SlotMap.FastKey key, Object target, Context cx, Scriptable start) {
        if (target instanceof ScriptableObject) {
            return ((ScriptableObject) target).isFastKeyValid(key);
        }
        return false;
    }

    public static Object invokeFastKey(
            SlotMap.FastKey key, Object target, Context cx, Scriptable start) {
        return ((ScriptableObject) target).getFast(key, start);
    }

    static class FallbackLinker implements GuardingDynamicLinker {
        @Override
        public GuardedInvocation getGuardedInvocation(LinkRequest req, LinkerServices svcs)
                throws NoSuchMethodException, IllegalAccessException {
            Operation namedOp = req.getCallSiteDescriptor().getOperation();
            if (!(namedOp instanceof NamedOperation)) {
                throw new UnsupportedOperationException("Only named operations supported");
            }
            String propertyName = (String) ((NamedOperation) namedOp).getName();

            Operation nsOp = ((NamedOperation) namedOp).getBaseOperation();
            if (!(nsOp instanceof NamespaceOperation)) {
                throw new UnsupportedOperationException("Only namespace operations supported");
            }
            if (((NamespaceOperation) nsOp).getNamespace(0) != StandardNamespace.PROPERTY) {
                throw new UnsupportedOperationException(
                        "Only property namespace operations supported");
            }

            MethodType mType = req.getCallSiteDescriptor().getMethodType();
            MethodHandles.Lookup lookup = req.getCallSiteDescriptor().getLookup();
            Operation op = ((NamespaceOperation) nsOp).getBaseOperation();

            if (op == StandardOperation.GET) {
                MethodType tt = mType.insertParameterTypes(1, String.class);
                MethodHandle getMethod =
                        lookup.findStatic(ScriptRuntime.class, "getObjectProp", tt);
                MethodHandle mh = MethodHandles.insertArguments(getMethod, 1, propertyName);
                return new GuardedInvocation(mh);

            } else if (op == RhinoOperation.GETNOWARN) {
                MethodType tt = mType.insertParameterTypes(1, String.class);
                MethodHandle getMethod =
                        lookup.findStatic(ScriptRuntime.class, "getObjectPropNoWarn", tt);
                MethodHandle mh = MethodHandles.insertArguments(getMethod, 1, propertyName);
                return new GuardedInvocation(mh);

            } else if (op == StandardOperation.SET) {
                MethodType tt = mType.insertParameterTypes(1, String.class);
                MethodHandle getMethod =
                        lookup.findStatic(ScriptRuntime.class, "setObjectProp", tt);
                MethodHandle mh = MethodHandles.insertArguments(getMethod, 1, propertyName);
                return new GuardedInvocation(mh);

            } else {
                throw new UnsupportedOperationException("Invalid operation " + op);
            }
        }
    }
}
