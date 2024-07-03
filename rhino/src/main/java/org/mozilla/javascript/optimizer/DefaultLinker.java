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
import jdk.dynalink.linker.GuardingDynamicLinker;
import jdk.dynalink.linker.LinkRequest;
import jdk.dynalink.linker.LinkerServices;
import org.mozilla.javascript.ScriptRuntime;

class DefaultLinker implements GuardingDynamicLinker {

    @Override
    public GuardedInvocation getGuardedInvocation(LinkRequest req, LinkerServices svc)
            throws Exception {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Operation op = req.getCallSiteDescriptor().getOperation();
        String name = getName(op);
        op = NamedOperation.getBaseOperation(op);
        if (NamespaceOperation.contains(op, StandardOperation.GET, StandardNamespace.PROPERTY)) {
            MethodType tt =
                    req.getCallSiteDescriptor()
                            .getMethodType()
                            .insertParameterTypes(1, String.class);
            MethodHandle mh = lookup.findStatic(ScriptRuntime.class, "getObjectProp", tt);
            mh = MethodHandles.insertArguments(mh, 1, name);
            return new GuardedInvocation(mh);
        } else if (NamespaceOperation.contains(
                op, RhinoOperation.GETNOWARN, StandardNamespace.PROPERTY)) {
            MethodType tt =
                    req.getCallSiteDescriptor()
                            .getMethodType()
                            .insertParameterTypes(1, String.class);
            MethodHandle mh = lookup.findStatic(ScriptRuntime.class, "getObjectPropNoWarn", tt);
            mh = MethodHandles.insertArguments(mh, 1, name);
            return new GuardedInvocation(mh);
        } else if (NamespaceOperation.contains(
                op, StandardOperation.SET, StandardNamespace.PROPERTY)) {
            MethodType tt =
                    req.getCallSiteDescriptor()
                            .getMethodType()
                            .insertParameterTypes(1, String.class);
            MethodHandle mh = lookup.findStatic(ScriptRuntime.class, "setObjectProp", tt);
            mh = MethodHandles.insertArguments(mh, 1, name);
            return new GuardedInvocation(mh);
        } else {
            throw new UnsupportedOperationException(op.toString());
        }
    }

    private String getName(Operation op) {
        Object nameObj = NamedOperation.getName(op);
        if (nameObj instanceof String) {
            return (String) nameObj;
        } else if (nameObj != null) {
            throw new UnsupportedOperationException(op.toString());
        } else {
            return null;
        }
    }
}
