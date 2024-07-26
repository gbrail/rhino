package org.mozilla.javascript.optimizer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import jdk.dynalink.NamespaceOperation;
import jdk.dynalink.Operation;
import jdk.dynalink.StandardNamespace;
import jdk.dynalink.StandardOperation;
import jdk.dynalink.linker.GuardedInvocation;
import jdk.dynalink.linker.GuardingDynamicLinker;
import jdk.dynalink.linker.LinkRequest;
import jdk.dynalink.linker.LinkerServices;
import org.mozilla.javascript.Callable;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

class CallLinker implements GuardingDynamicLinker {
    @Override
    public GuardedInvocation getGuardedInvocation(LinkRequest req, LinkerServices svc)
            throws Exception {
        if (req.isCallSiteUnstable()) {
            return null;
        }

        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Operation op = req.getCallSiteDescriptor().getOperation();

        if (NamespaceOperation.contains(op, StandardOperation.CALL, StandardNamespace.METHOD)) {
            Callable call = (Callable) req.getArguments()[0];
            MethodType guardType =
                    req.getCallSiteDescriptor()
                            .getMethodType()
                            .changeReturnType(Boolean.TYPE)
                            .insertParameterTypes(5, Callable.class);
            MethodHandle guardHandle =
                    lookup.findStatic(CallLinker.class, "testSavedCallable", guardType);
            guardHandle = MethodHandles.insertArguments(guardHandle, 5, call);

            MethodHandle callHandle =
                    lookup.findVirtual(
                            Callable.class,
                            "call",
                            MethodType.methodType(
                                    Object.class,
                                    Context.class,
                                    Scriptable.class,
                                    Scriptable.class,
                                    Object[].class));
            callHandle =
                    MethodHandles.permuteArguments(
                            callHandle, req.getCallSiteDescriptor().getMethodType(), 0, 3, 4, 1, 2);

            if (DefaultLinker.DEBUG) {
                System.out.println("Call link: " + op);
            }
            return new GuardedInvocation(callHandle, guardHandle);
            /* Don't know why that doesn't work
            } else if (NamespaceOperation.contains(op, RhinoOperation.CALL_0, StandardNamespace.METHOD)) {
                Callable call = (Callable) req.getArguments()[0];
                MethodType guardType =
                    req.getCallSiteDescriptor()
                            .getMethodType()
                            .changeReturnType(Boolean.TYPE)
                            .insertParameterTypes(4, Callable.class);
                MethodHandle guardHandle =
                    lookup.findStatic(CallLinker.class, "testSavedCallable0", guardType);
                guardHandle = MethodHandles.insertArguments(guardHandle, 4, call);

                System.out.println("incoming = " + req.getCallSiteDescriptor().getMethodType());
                MethodHandle callHandle =
                    lookup.findVirtual(Callable.class, "call",
                    MethodType.methodType(Object.class, Context.class, Scriptable.class,
                        Scriptable.class, Object[].class));
                System.out.println("target = " + callHandle.type());
                callHandle = MethodHandles.insertArguments(callHandle, 4, ScriptRuntime.emptyArgs);
                System.out.println("insert = " + callHandle.type());
                callHandle = MethodHandles.permuteArguments(callHandle,
                    req.getCallSiteDescriptor().getMethodType(),
                    0, 2, 3, 1);
                System.out.println("permute = " + callHandle.type());


                if (DefaultLinker.DEBUG) {
                    System.out.println("Call link: " + op);
                }
                return new GuardedInvocation(callHandle, guardHandle);
            */
        } else if (NamespaceOperation.contains(
                op, RhinoOperation.CALL_1, StandardNamespace.METHOD)) {
            Callable call = (Callable) req.getArguments()[0];
            MethodType guardType =
                    req.getCallSiteDescriptor()
                            .getMethodType()
                            .changeReturnType(Boolean.TYPE)
                            .insertParameterTypes(5, Callable.class);
            MethodHandle guardHandle =
                    lookup.findStatic(CallLinker.class, "testSavedCallable1", guardType);
            guardHandle = MethodHandles.insertArguments(guardHandle, 5, call);

            MethodHandle concat =
                    lookup.findStatic(
                            CallLinker.class,
                            "concat1",
                            MethodType.methodType(Object[].class, Object.class));

            MethodHandle callHandle =
                    lookup.findVirtual(
                            Callable.class,
                            "call",
                            MethodType.methodType(
                                    Object.class,
                                    Context.class,
                                    Scriptable.class,
                                    Scriptable.class,
                                    Object[].class));
            callHandle = MethodHandles.collectArguments(callHandle, 4, concat);
            callHandle =
                    MethodHandles.permuteArguments(
                            callHandle, req.getCallSiteDescriptor().getMethodType(), 0, 3, 4, 1, 2);

            if (DefaultLinker.DEBUG) {
                System.out.println("Call link: " + op);
            }
            return new GuardedInvocation(callHandle, guardHandle);
        } else if (NamespaceOperation.contains(
                op, RhinoOperation.CALL_2, StandardNamespace.METHOD)) {
            Callable call = (Callable) req.getArguments()[0];
            MethodType guardType =
                    req.getCallSiteDescriptor()
                            .getMethodType()
                            .changeReturnType(Boolean.TYPE)
                            .insertParameterTypes(6, Callable.class);
            MethodHandle guardHandle =
                    lookup.findStatic(CallLinker.class, "testSavedCallable2", guardType);
            guardHandle = MethodHandles.insertArguments(guardHandle, 6, call);

            MethodHandle concat =
                    lookup.findStatic(
                            CallLinker.class,
                            "concat2",
                            MethodType.methodType(Object[].class, Object.class, Object.class));

            MethodHandle callHandle =
                    lookup.findVirtual(
                            Callable.class,
                            "call",
                            MethodType.methodType(
                                    Object.class,
                                    Context.class,
                                    Scriptable.class,
                                    Scriptable.class,
                                    Object[].class));
            callHandle = MethodHandles.collectArguments(callHandle, 4, concat);
            callHandle =
                    MethodHandles.permuteArguments(
                            callHandle,
                            req.getCallSiteDescriptor().getMethodType(),
                            0,
                            4,
                            5,
                            1,
                            2,
                            3);

            if (DefaultLinker.DEBUG) {
                System.out.println("Call link: " + op);
            }
            return new GuardedInvocation(callHandle, guardHandle);
        }

        // Fall through to other linkers
        return null;
    }

    static boolean testSavedCallable(
            Callable call,
            Scriptable thisObj,
            Object[] args,
            Context cx,
            Scriptable scope,
            Callable savedCallable) {
        return savedCallable == call;
    }

    static boolean testSavedCallable0(
            Callable call,
            Scriptable thisObj,
            Context cx,
            Scriptable scope,
            Callable savedCallable) {
        return savedCallable == call;
    }

    static boolean testSavedCallable1(
            Callable call,
            Scriptable thisObj,
            Object arg,
            Context cx,
            Scriptable scope,
            Callable savedCallable) {
        return savedCallable == call;
    }

    static boolean testSavedCallable2(
            Callable call,
            Scriptable thisObj,
            Object arg,
            Object arg1,
            Context cx,
            Scriptable scope,
            Callable savedCallable) {
        return savedCallable == call;
    }

    static Object[] concat1(Object arg) {
        return new Object[] {arg};
    }

    static Object[] concat2(Object a0, Object a1) {
        return new Object[] {a0, a1};
    }
}
