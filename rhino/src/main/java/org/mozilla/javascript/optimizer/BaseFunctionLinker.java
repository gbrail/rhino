package org.mozilla.javascript.optimizer;

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
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

@SuppressWarnings("AndroidJdkLibsChecker")
class BaseFunctionLinker implements TypeBasedGuardingDynamicLinker {
    @Override
    public boolean canLinkType(Class<?> type) {
        return BaseFunction.class.isAssignableFrom(type);
    }

    @Override
    public GuardedInvocation getGuardedInvocation(LinkRequest req, LinkerServices svc) throws Exception {
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
                && "prototype".equals(name)
                && (target instanceof BaseFunction)) {
            MethodHandle mh = lookup.findStatic(BaseFunctionLinker.class, "getPrototype", mType);
            MethodHandle guard = Guards.asType(Guards.getInstanceOfGuard(BaseFunction.class), mType);
            if (DefaultLinker.DEBUG) {
                System.out.println(rootOp + " native function");
            }
            return new GuardedInvocation(mh, guard);
        }

        return null;
    }

    @SuppressWarnings("unused")
    private static Object getPrototype(Object target, Context cx, Scriptable scope) {
        BaseFunction f = (BaseFunction) target;
        return f.getPrototypeProperty();
    }
}
