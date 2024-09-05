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
    static final boolean DEBUG = true;

    @Override
    public GuardedInvocation getGuardedInvocation(LinkRequest req, LinkerServices svc)
            throws Exception {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Operation rootOp = req.getCallSiteDescriptor().getOperation();

        String name = getName(rootOp);
        Operation op = NamedOperation.getBaseOperation(rootOp);

        if (!(op instanceof NamespaceOperation)) {
            throw new UnsupportedOperationException(op.toString());
        }
        NamespaceOperation nsOp = (NamespaceOperation) op;
        op = NamespaceOperation.getBaseOperation(op);

        GuardedInvocation invocation = null;
        if (nsOp.contains(StandardNamespace.PROPERTY)) {
            invocation =
                    getPropertyInvocation(
                            lookup, req.getCallSiteDescriptor().getMethodType(), op, name);
        } else if (nsOp.contains(RhinoNamespace.NAME)) {
            invocation =
                    getNameInvocation(
                            lookup, req.getCallSiteDescriptor().getMethodType(), op, name);
        }

        if (invocation != null) {
            if (DEBUG) {
                System.out.println(rootOp + ": default link");
            }
            return invocation;
        }
        throw new UnsupportedOperationException(rootOp.toString());
    }

    private GuardedInvocation getPropertyInvocation(
            MethodHandles.Lookup lookup, MethodType mType, Operation op, String name)
            throws NoSuchMethodException, IllegalAccessException {
        MethodType tt;
        MethodHandle mh = null;

        if (StandardOperation.GET.equals(op)) {
            tt = mType.insertParameterTypes(1, String.class);
            mh = lookup.findStatic(ScriptRuntime.class, "getObjectProp", tt);
            mh = MethodHandles.insertArguments(mh, 1, name);
        } else if (RhinoOperation.GETNOWARN.equals(op)) {
            tt = mType.insertParameterTypes(1, String.class);
            mh = lookup.findStatic(ScriptRuntime.class, "getObjectPropNoWarn", tt);
            mh = MethodHandles.insertArguments(mh, 1, name);
        } else if (RhinoOperation.GETWITHTHIS.equals(op)) {
            tt = mType.insertParameterTypes(1, String.class);
            mh = lookup.findStatic(ScriptRuntime.class, "getPropFunctionAndThis", tt);
            mh = MethodHandles.insertArguments(mh, 1, name);
        } else if (StandardOperation.SET.equals(op)) {
            tt = mType.insertParameterTypes(1, String.class);
            mh = lookup.findStatic(ScriptRuntime.class, "setObjectProp", tt);
            mh = MethodHandles.insertArguments(mh, 1, name);
        }

        if (mh != null) {
            return new GuardedInvocation(mh);
        }
        return null;
    }

    private GuardedInvocation getNameInvocation(
            MethodHandles.Lookup lookup, MethodType mType, Operation op, String name)
            throws NoSuchMethodException, IllegalAccessException {
        MethodType tt;
        MethodHandle mh = null;

        if (RhinoOperation.BIND.equals(op)) {
            tt = mType.insertParameterTypes(2, String.class);
            mh = lookup.findStatic(ScriptRuntime.class, "bind", tt);
            mh = MethodHandles.insertArguments(mh, 2, name);
        } else if (StandardOperation.GET.equals(op)) {
            tt = mType.insertParameterTypes(2, String.class);
            mh = lookup.findStatic(ScriptRuntime.class, "name", tt);
            mh = MethodHandles.insertArguments(mh, 2, name);
        } else if (RhinoOperation.GETWITHTHIS.equals(op)) {
            tt = mType.insertParameterTypes(0, String.class);
            mh = lookup.findStatic(ScriptRuntime.class, "getNameFunctionAndThis", tt);
            mh = MethodHandles.insertArguments(mh, 0, name);
        }

        if (mh != null) {
            return new GuardedInvocation(mh);
        }
        return null;
    }

    // If the operation is a named operation, then return the name
    static String getName(Operation op) {
        Object nameObj = NamedOperation.getName(op);
        if (nameObj instanceof String) {
            // Interning this name is super duper important if we want to have good performance
            return ((String) nameObj).intern();
        } else if (nameObj != null) {
            throw new UnsupportedOperationException(op.toString());
        } else {
            return "";
        }
    }
}
