package org.mozilla.javascript.optimizer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import jdk.dynalink.NamedOperation;
import jdk.dynalink.NamespaceOperation;
import jdk.dynalink.Operation;
import jdk.dynalink.StandardNamespace;
import jdk.dynalink.StandardOperation;
import jdk.dynalink.linker.GuardedInvocation;
import jdk.dynalink.linker.LinkRequest;
import jdk.dynalink.linker.LinkerServices;
import jdk.dynalink.linker.TypeBasedGuardingDynamicLinker;
import jdk.dynalink.linker.support.Guards;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;

@SuppressWarnings("AndroidJdkLibsChecker")
class NativeArrayLinker implements TypeBasedGuardingDynamicLinker {
    @Override
    public boolean canLinkType(Class<?> type) {
        return NativeArray.class.isAssignableFrom(type);
    }

    @Override
    public GuardedInvocation getGuardedInvocation(LinkRequest req, LinkerServices svc)
            throws Exception {
        if (req.isCallSiteUnstable()) {
            return null;
        }

        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Operation rootOp = req.getCallSiteDescriptor().getOperation();
        MethodType mType = req.getCallSiteDescriptor().getMethodType();
        String name = DefaultLinker.getName(rootOp);
        Operation op = NamedOperation.getBaseOperation(rootOp);
        Object target = req.getReceiver();

        if ((NamespaceOperation.contains(op, StandardOperation.GET, StandardNamespace.PROPERTY)
                        || NamespaceOperation.contains(
                                op, RhinoOperation.GETNOWARN, StandardNamespace.PROPERTY))
                && "length".equals(name)
                && (target instanceof NativeArray)) {
            // Replace getting the "length" of a native array with the optimized
            // code below.
            MethodHandle mh = lookup.findStatic(NativeArrayLinker.class, "getArrayLength", mType);
            // The guard will check to see if the target is an instance of NativeArray
            MethodHandle guard = Guards.asType(Guards.getInstanceOfGuard(NativeArray.class), mType);
            if (DefaultLinker.DEBUG) {
                System.out.println(rootOp + " native array");
            }
            return new GuardedInvocation(mh, guard);
        }

        return null;
    }

    @SuppressWarnings("unused")
    private static Object getArrayLength(Object target, Context cx, Scriptable scope) {
        // We can assume this cast will work because the guard checked it for us
        NativeArray a = (NativeArray) target;
        long length = a.getLength();
        if (length >= 0L && length < Integer.MAX_VALUE) {
            // Try to optimize to a boxed integer if we can
            return Integer.valueOf((int) length);
        }
        return ScriptRuntime.toNumber(length);
    }
}
