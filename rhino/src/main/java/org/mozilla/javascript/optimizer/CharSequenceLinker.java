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

@SuppressWarnings("AndroidJdkLibsChecker")
class CharSequenceLinker implements TypeBasedGuardingDynamicLinker {
    @Override
    public boolean canLinkType(Class<?> type) {
        return CharSequence.class.isAssignableFrom(type);
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
                && target instanceof CharSequence
                && req.getArguments()[1] instanceof CharSequence) {
            MethodHandle mh = lookup.findStatic(CharSequenceLinker.class, "add", mType);
            MethodType guardType = mType.changeReturnType(Boolean.TYPE);
            MethodHandle guard = lookup.findStatic(CharSequenceLinker.class, "testAdd", guardType);
            if (DefaultLinker.DEBUG) {
                System.out.println(rootOp + " char sequence add");
            }
            return new GuardedInvocation(mh, guard);
        }

        return null;
    }

    @SuppressWarnings("unused")
    private static boolean testAdd(Object rawLval, Object rawRval, Context cx) {
        return rawLval instanceof CharSequence && rawRval instanceof CharSequence;
    }

    @SuppressWarnings("unused")
    private static Object add(Object rawLval, Object rawRval, Context cx) {
        CharSequence lval = (CharSequence) rawLval;
        CharSequence rval = (CharSequence) rawRval;
        return new ConsString(lval.toString(), rval.toString());
    }
}
