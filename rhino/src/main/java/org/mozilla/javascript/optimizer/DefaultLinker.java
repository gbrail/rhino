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
import org.mozilla.javascript.Callable;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;

class DefaultLinker implements GuardingDynamicLinker {

    static final boolean DEBUG = false;

    @Override
    public GuardedInvocation getGuardedInvocation(LinkRequest req, LinkerServices svc)
            throws Exception {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Operation op = req.getCallSiteDescriptor().getOperation();
        String name = getName(op);
        op = NamedOperation.getBaseOperation(op);
        MethodHandle mh;

        if (DEBUG) {
            if (name == null) {
                System.out.println("Default link: " + op);
            } else {
                System.out.println("Default link: " + op + ':' + name);
            }
        }

        if (NamespaceOperation.contains(op, StandardOperation.GET, StandardNamespace.PROPERTY)) {
            MethodType tt =
                    req.getCallSiteDescriptor()
                            .getMethodType()
                            .insertParameterTypes(1, String.class);
            mh = lookup.findStatic(ScriptRuntime.class, "getObjectProp", tt);
            mh = MethodHandles.insertArguments(mh, 1, name);
        } else if (NamespaceOperation.contains(
                op, RhinoOperation.GETNOWARN, StandardNamespace.PROPERTY)) {
            MethodType tt =
                    req.getCallSiteDescriptor()
                            .getMethodType()
                            .insertParameterTypes(1, String.class);
            mh = lookup.findStatic(ScriptRuntime.class, "getObjectPropNoWarn", tt);
            mh = MethodHandles.insertArguments(mh, 1, name);
        } else if (NamespaceOperation.contains(
                op, RhinoOperation.GETWITHTHIS, StandardNamespace.PROPERTY)) {
            MethodType tt =
                    req.getCallSiteDescriptor()
                            .getMethodType()
                            .insertParameterTypes(1, String.class);
            mh = lookup.findStatic(ScriptRuntime.class, "getPropFunctionAndThis", tt);
            mh = MethodHandles.insertArguments(mh, 1, name);
        } else if (NamespaceOperation.contains(
                op, StandardOperation.SET, StandardNamespace.PROPERTY)) {
            MethodType tt =
                    req.getCallSiteDescriptor()
                            .getMethodType()
                            .insertParameterTypes(1, String.class);
            mh = lookup.findStatic(ScriptRuntime.class, "setObjectProp", tt);
            mh = MethodHandles.insertArguments(mh, 1, name);
        } else if (NamespaceOperation.contains(
                op, RhinoOperation.CALL_0, StandardNamespace.PROPERTY)) {
            MethodType tt =
                    req.getCallSiteDescriptor()
                            .getMethodType()
                            .insertParameterTypes(1, String.class);
            mh = lookup.findStatic(OptRuntime.class, "callProp0", tt);
            mh = MethodHandles.insertArguments(mh, 1, name);

        } else if (NamespaceOperation.contains(op, RhinoOperation.BIND, RhinoNamespace.NAME)) {
            MethodType tt =
                    req.getCallSiteDescriptor()
                            .getMethodType()
                            .insertParameterTypes(2, String.class);
            mh = lookup.findStatic(DefaultLinker.class, "bind", tt);
            mh = MethodHandles.insertArguments(mh, 2, name);
        } else if (NamespaceOperation.contains(op, StandardOperation.GET, RhinoNamespace.NAME)) {
            MethodType tt =
                    req.getCallSiteDescriptor()
                            .getMethodType()
                            .insertParameterTypes(2, String.class);
            mh = lookup.findStatic(DefaultLinker.class, "name", tt);
            mh = MethodHandles.insertArguments(mh, 2, name);
        } else if (NamespaceOperation.contains(
                op, RhinoOperation.GETWITHTHIS, RhinoNamespace.NAME)) {
            MethodType tt =
                    req.getCallSiteDescriptor()
                            .getMethodType()
                            .insertParameterTypes(2, String.class);
            mh = lookup.findStatic(DefaultLinker.class, "getNameFunctionAndThis", tt);
            mh = MethodHandles.insertArguments(mh, 2, name);
        } else if (NamespaceOperation.contains(op, StandardOperation.SET, RhinoNamespace.NAME)) {
            MethodType tt =
                    req.getCallSiteDescriptor()
                            .getMethodType()
                            .insertParameterTypes(4, String.class);
            mh = lookup.findStatic(ScriptRuntime.class, "setName", tt);
            mh = MethodHandles.insertArguments(mh, 4, name);
        } else if (NamespaceOperation.contains(op, RhinoOperation.SETSTRICT, RhinoNamespace.NAME)) {
            MethodType tt =
                    req.getCallSiteDescriptor()
                            .getMethodType()
                            .insertParameterTypes(4, String.class);
            mh = lookup.findStatic(ScriptRuntime.class, "strictSetName", tt);
            mh = MethodHandles.insertArguments(mh, 4, name);
        } else if (NamespaceOperation.contains(op, StandardOperation.CALL, RhinoNamespace.NAME)) {
            MethodType tt =
                    req.getCallSiteDescriptor()
                            .getMethodType()
                            .insertParameterTypes(1, String.class);
            mh = lookup.findStatic(OptRuntime.class, "callName", tt);
            mh = MethodHandles.insertArguments(mh, 1, name);
        } else if (NamespaceOperation.contains(op, RhinoOperation.CALL_0, RhinoNamespace.NAME)) {
            MethodType tt =
                    req.getCallSiteDescriptor()
                            .getMethodType()
                            .insertParameterTypes(0, String.class);
            mh = lookup.findStatic(OptRuntime.class, "callName0", tt);
            mh = MethodHandles.insertArguments(mh, 0, name);

        } else if (NamespaceOperation.contains(
                op, StandardOperation.CALL, StandardNamespace.METHOD)) {
            mh =
                    lookup.findStatic(
                            OptRuntime.class, "callN", req.getCallSiteDescriptor().getMethodType());
        } else if (NamespaceOperation.contains(
                op, RhinoOperation.CALL_0, StandardNamespace.METHOD)) {
            mh =
                    lookup.findStatic(
                            OptRuntime.class, "call0", req.getCallSiteDescriptor().getMethodType());
        } else if (NamespaceOperation.contains(
                op, RhinoOperation.CALL_1, StandardNamespace.METHOD)) {
            mh =
                    lookup.findStatic(
                            OptRuntime.class, "call1", req.getCallSiteDescriptor().getMethodType());
        } else if (NamespaceOperation.contains(
                op, RhinoOperation.CALL_2, StandardNamespace.METHOD)) {
            mh =
                    lookup.findStatic(
                            OptRuntime.class, "call2", req.getCallSiteDescriptor().getMethodType());

        } else if (NamespaceOperation.contains(op, RhinoOperation.ADD, RhinoNamespace.MATH)) {
            mh =
                    lookup.findStatic(
                            ScriptRuntime.class,
                            "add",
                            req.getCallSiteDescriptor().getMethodType());
        } else if (NamespaceOperation.contains(op, RhinoOperation.SUBTRACT, RhinoNamespace.MATH)) {
            mh =
                    lookup.findStatic(
                            ScriptRuntime.class,
                            "subtract",
                            req.getCallSiteDescriptor().getMethodType());
        } else if (NamespaceOperation.contains(op, RhinoOperation.MULTIPLY, RhinoNamespace.MATH)) {
            mh =
                    lookup.findStatic(
                            ScriptRuntime.class,
                            "multiply",
                            req.getCallSiteDescriptor().getMethodType());
        } else if (NamespaceOperation.contains(op, RhinoOperation.DIVIDE, RhinoNamespace.MATH)) {
            mh =
                    lookup.findStatic(
                            ScriptRuntime.class,
                            "divide",
                            req.getCallSiteDescriptor().getMethodType());
        } else if (NamespaceOperation.contains(op, RhinoOperation.REMAINDER, RhinoNamespace.MATH)) {
            mh =
                    lookup.findStatic(
                            ScriptRuntime.class,
                            "remainder",
                            req.getCallSiteDescriptor().getMethodType());
        } else if (NamespaceOperation.contains(op, RhinoOperation.NEGATE, RhinoNamespace.MATH)) {
            mh =
                    lookup.findStatic(
                            ScriptRuntime.class,
                            "negate",
                            req.getCallSiteDescriptor().getMethodType());
        } else if (NamespaceOperation.contains(
                op, RhinoOperation.EXPONENTIATE, RhinoNamespace.MATH)) {
            mh =
                    lookup.findStatic(
                            ScriptRuntime.class,
                            "exponentiate",
                            req.getCallSiteDescriptor().getMethodType());
        } else if (NamespaceOperation.contains(
                op, RhinoOperation.BITWISE_NOT, RhinoNamespace.MATH)) {
            mh =
                    lookup.findStatic(
                            ScriptRuntime.class,
                            "bitwiseNOT",
                            req.getCallSiteDescriptor().getMethodType());
        } else if (NamespaceOperation.contains(
                op, RhinoOperation.BITWISE_OR, RhinoNamespace.MATH)) {
            mh =
                    lookup.findStatic(
                            ScriptRuntime.class,
                            "bitwiseOR",
                            req.getCallSiteDescriptor().getMethodType());
        } else if (NamespaceOperation.contains(
                op, RhinoOperation.BITWISE_XOR, RhinoNamespace.MATH)) {
            mh =
                    lookup.findStatic(
                            ScriptRuntime.class,
                            "bitwiseXOR",
                            req.getCallSiteDescriptor().getMethodType());
        } else if (NamespaceOperation.contains(
                op, RhinoOperation.BITWISE_AND, RhinoNamespace.MATH)) {
            mh =
                    lookup.findStatic(
                            ScriptRuntime.class,
                            "bitwiseAND",
                            req.getCallSiteDescriptor().getMethodType());
        } else if (NamespaceOperation.contains(
                op, RhinoOperation.SIGNED_RIGHT_SHIFT, RhinoNamespace.MATH)) {
            mh =
                    lookup.findStatic(
                            ScriptRuntime.class,
                            "signedRightShift",
                            req.getCallSiteDescriptor().getMethodType());
        } else if (NamespaceOperation.contains(
                op, RhinoOperation.LEFT_SHIFT, RhinoNamespace.MATH)) {
            mh =
                    lookup.findStatic(
                            ScriptRuntime.class,
                            "leftShift",
                            req.getCallSiteDescriptor().getMethodType());

        } else {
            throw new UnsupportedOperationException(op.toString());
        }

        return new GuardedInvocation(mh);
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

    static Scriptable bind(Scriptable scope, Context cx, String name) {
        return ScriptRuntime.bind(cx, scope, name);
    }

    static Object name(Scriptable scope, Context cx, String name) {
        return ScriptRuntime.name(cx, scope, name);
    }

    static Callable getNameFunctionAndThis(Scriptable target, Context cx, String name) {
        return ScriptRuntime.getNameFunctionAndThis(name, cx, target);
    }
}
