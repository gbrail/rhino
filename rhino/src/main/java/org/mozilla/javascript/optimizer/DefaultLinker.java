package org.mozilla.javascript.optimizer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.SwitchPoint;
import jdk.dynalink.NamedOperation;
import jdk.dynalink.NamespaceOperation;
import jdk.dynalink.Operation;
import jdk.dynalink.StandardNamespace;
import jdk.dynalink.StandardOperation;
import jdk.dynalink.linker.GuardedInvocation;
import jdk.dynalink.linker.GuardingDynamicLinker;
import jdk.dynalink.linker.LinkRequest;
import jdk.dynalink.linker.LinkerServices;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;

class DefaultLinker implements GuardingDynamicLinker {

    static final boolean DEBUG = true;

    static final SwitchPoint[] EMPTY_SWITCH_POINTS = new SwitchPoint[] {};

    @Override
    public GuardedInvocation getGuardedInvocation(LinkRequest req, LinkerServices svc)
            throws Exception {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Operation op = req.getCallSiteDescriptor().getOperation();
        String name = getName(op);
        op = NamedOperation.getBaseOperation(op);

        if (DEBUG) {
            System.out.println("Default link: " + op + ':' + name);
        }

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

        } else if (NamespaceOperation.contains(op, RhinoOperation.BIND, RhinoNamespace.NAME)) {
            MethodType tt =
                    req.getCallSiteDescriptor()
                            .getMethodType()
                            .insertParameterTypes(2, String.class);
            MethodHandle mh = lookup.findStatic(DefaultLinker.class, "bind", tt);
            mh = MethodHandles.insertArguments(mh, 2, name);
            return new GuardedInvocation(mh);
        } else if (NamespaceOperation.contains(op, StandardOperation.GET, RhinoNamespace.NAME)) {
            MethodType tt =
                    req.getCallSiteDescriptor()
                            .getMethodType()
                            .insertParameterTypes(2, String.class);
            MethodHandle mh = lookup.findStatic(DefaultLinker.class, "name", tt);
            mh = MethodHandles.insertArguments(mh, 2, name);
            return new GuardedInvocation(mh);
        } else if (NamespaceOperation.contains(op, StandardOperation.SET, RhinoNamespace.NAME)) {
            MethodType tt =
                    req.getCallSiteDescriptor()
                            .getMethodType()
                            .insertParameterTypes(4, String.class);
            MethodHandle mh = lookup.findStatic(ScriptRuntime.class, "setName", tt);
            mh = MethodHandles.insertArguments(mh, 4, name);
            return new GuardedInvocation(mh);
        } else if (NamespaceOperation.contains(op, RhinoOperation.SETSTRICT, RhinoNamespace.NAME)) {
            MethodType tt =
                    req.getCallSiteDescriptor()
                            .getMethodType()
                            .insertParameterTypes(4, String.class);
            MethodHandle mh = lookup.findStatic(ScriptRuntime.class, "strictSetName", tt);
            mh = MethodHandles.insertArguments(mh, 4, name);
            return new GuardedInvocation(mh);
        }

        // Fall through to exception since this is the default linker
        throw new UnsupportedOperationException(op.toString());
    }

    static String getName(Operation op) {
        Object nameObj = NamedOperation.getName(op);
        if (nameObj instanceof String) {
            // Interning this name is super duper important if we want to have good performance
            return ((String) nameObj).intern();
        } else if (nameObj != null) {
            throw new UnsupportedOperationException(op.toString());
        } else {
            return null;
        }
    }

    @SuppressWarnings("unused")
    private static Scriptable bind(Scriptable scope, Context cx, String name) {
        return ScriptRuntime.bind(cx, scope, name);
    }

    @SuppressWarnings("unused")
    private static Object name(Scriptable scope, Context cx, String name) {
        return ScriptRuntime.name(cx, scope, name);
    }
}
