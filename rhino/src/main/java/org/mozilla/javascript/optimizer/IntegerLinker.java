package org.mozilla.javascript.optimizer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import jdk.dynalink.NamespaceOperation;
import jdk.dynalink.Operation;
import jdk.dynalink.linker.GuardedInvocation;
import jdk.dynalink.linker.LinkRequest;
import jdk.dynalink.linker.LinkerServices;
import jdk.dynalink.linker.TypeBasedGuardingDynamicLinker;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptRuntime;

@SuppressWarnings("AndroidJdkLibsChecker")
class IntegerLinker implements TypeBasedGuardingDynamicLinker {
    @Override
    public boolean canLinkType(Class<?> type) {
        return Integer.class.equals(type);
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
        Object target = req.getReceiver();

        if (NamespaceOperation.contains(
                        req.getCallSiteDescriptor().getOperation(),
                        RhinoOperation.ADD,
                        RhinoNamespace.MATH)
                && target instanceof Integer
                && req.getArguments()[1] instanceof Integer) {
            MethodHandle mh = lookup.findStatic(IntegerLinker.class, "add", mType);
            MethodType guardType = mType.changeReturnType(Boolean.TYPE);
            MethodHandle guard = lookup.findStatic(IntegerLinker.class, "testAdd", guardType);
            if (DefaultLinker.DEBUG) {
                System.out.println(rootOp + " integer add");
            }
            return new GuardedInvocation(mh, guard);
        }

        return null;
    }

    @SuppressWarnings("unused")
    private static boolean testAdd(Object rawLval, Object rawRval, Context cx) {
        return rawLval instanceof Integer && rawRval instanceof Integer;
    }

    @SuppressWarnings("unused")
    private static Object add(Object rawLval, Object rawRval, Context cx) {
        Integer lval = (Integer) rawLval;
        Integer rval = (Integer) rawRval;
        return ScriptRuntime.add(lval, rval);
    }
}
