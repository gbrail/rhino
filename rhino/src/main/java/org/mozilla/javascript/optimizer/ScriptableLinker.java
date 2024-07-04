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
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;

class ScriptableLinker implements TypeBasedGuardingDynamicLinker {

    @Override
    public boolean canLinkType(Class<?> type) {
        return Scriptable.class.isAssignableFrom(type);
    }

    @Override
    public GuardedInvocation getGuardedInvocation(LinkRequest req, LinkerServices svc)
            throws Exception {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Operation op = req.getCallSiteDescriptor().getOperation();
        String name = DefaultLinker.getName(op);
        op = NamedOperation.getBaseOperation(op);

        if (DefaultLinker.DEBUG) {
            System.out.println("Scriptable link attempt: " + op + ':' + name);
        }

        if (NamespaceOperation.contains(op, StandardOperation.GET, StandardNamespace.PROPERTY)) {
            MethodType tt =
                    req.getCallSiteDescriptor()
                            .getMethodType()
                            .changeParameterType(0, Scriptable.class)
                            .insertParameterTypes(1, String.class);
            MethodHandle mh = lookup.findStatic(ScriptRuntime.class, "getObjectProp", tt);
            mh = MethodHandles.insertArguments(mh, 1, name);
            return new GuardedInvocation(mh, null, DefaultLinker.EMPTY_SWITCH_POINTS, ClassCastException.class);
        } else if (NamespaceOperation.contains(
                op, RhinoOperation.GETNOWARN, StandardNamespace.PROPERTY)) {
            MethodType tt =
                    req.getCallSiteDescriptor()
                            .getMethodType()
                            .changeParameterType(0, Scriptable.class)
                            .insertParameterTypes(1, String.class);
            MethodHandle mh = lookup.findStatic(ScriptRuntime.class, "getObjectPropNoWarn", tt);
            mh = MethodHandles.insertArguments(mh, 1, name);
            return new GuardedInvocation(mh, null, DefaultLinker.EMPTY_SWITCH_POINTS, ClassCastException.class);
        } else if (NamespaceOperation.contains(
                op, StandardOperation.SET, StandardNamespace.PROPERTY)) {
            MethodType tt =
                    req.getCallSiteDescriptor()
                            .getMethodType()
                            .changeParameterType(0, Scriptable.class)
                            .insertParameterTypes(1, String.class);
            MethodHandle mh = lookup.findStatic(ScriptRuntime.class, "setObjectProp", tt);
            mh = MethodHandles.insertArguments(mh, 1, name);
            return new GuardedInvocation(mh, null, DefaultLinker.EMPTY_SWITCH_POINTS, ClassCastException.class);

        } else if (NamespaceOperation.contains(op, StandardOperation.SET, RhinoNamespace.NAME)) {
            // If we get here, first argument is not null and we can skip the check
            MethodType tt =
                    req.getCallSiteDescriptor()
                            .getMethodType()
                            .insertParameterTypes(4, String.class);
            MethodHandle mh = lookup.findStatic(ScriptRuntime.class, "setBoundName", tt);
            mh = MethodHandles.insertArguments(mh, 4, name);
            return new GuardedInvocation(mh, null, DefaultLinker.EMPTY_SWITCH_POINTS, NullPointerException.class);

        } else {
            if (DefaultLinker.DEBUG) {
                System.out.println("  Scriptable link attempt fell through");
            }
            return null;
        }
    }
}
