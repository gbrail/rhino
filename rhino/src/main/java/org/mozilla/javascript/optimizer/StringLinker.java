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
import org.mozilla.javascript.ConsString;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptRuntime;

@SuppressWarnings("AndroidJdkLibsChecker")
class StringLinker implements TypeBasedGuardingDynamicLinker {
    @Override
    public boolean canLinkType(Class<?> type) {
        return String.class.equals(type);
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
                && target instanceof String) {
            MethodHandle mh;
            MethodHandle guard;
            MethodType guardType = mType.changeReturnType(Boolean.TYPE);

            if (req.getArguments()[1] instanceof String) {
                mh = lookup.findStatic(StringLinker.class, "addStrings", mType);
                guard = lookup.findStatic(StringLinker.class, "testAddStrings", guardType);
                if (DefaultLinker.DEBUG) {
                    System.out.println(rootOp + " string + string");
                }
            } else {
                mh = lookup.findStatic(StringLinker.class, "add", mType);
                guard = lookup.findStatic(StringLinker.class, "testAdd", guardType);
                if (DefaultLinker.DEBUG) {
                    System.out.println(rootOp + " string + non-string");
                }
            }
            return new GuardedInvocation(mh, guard);
        }

        return null;
    }

    @SuppressWarnings("unused")
    private static boolean testAddStrings(Object rawLval, Object rawRval, Context cx) {
        return rawLval instanceof String && rawRval instanceof String;
    }

    @SuppressWarnings("unused")
    private static boolean testAdd(Object rawLval, Object rawRval, Context cx) {
        return rawLval instanceof String;
    }

    @SuppressWarnings("unused")
    private static Object addStrings(Object rawLval, Object rawRval, Context cx) {
        String lval = (String) rawLval;
        String rval = (String) rawRval;
        return new ConsString(lval, rval);
    }

    @SuppressWarnings("unused")
    private static Object add(Object rawLval, Object rawRval, Context cx) {
        String lval = (String) rawLval;
        String rval = ScriptRuntime.toString(rawRval);
        return new ConsString(lval, rval);
    }
}
