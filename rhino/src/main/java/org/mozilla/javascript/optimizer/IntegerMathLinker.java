package org.mozilla.javascript.optimizer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import jdk.dynalink.NamedOperation;
import jdk.dynalink.NamespaceOperation;
import jdk.dynalink.Operation;
import jdk.dynalink.linker.GuardedInvocation;
import jdk.dynalink.linker.LinkRequest;
import jdk.dynalink.linker.LinkerServices;
import jdk.dynalink.linker.TypeBasedGuardingDynamicLinker;
import jdk.dynalink.linker.support.Guards;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptRuntime;

class IntegerMathLinker implements TypeBasedGuardingDynamicLinker {

    @Override
    public boolean canLinkType(Class<?> type) {
        return Integer.class.isAssignableFrom(type);
    }

    @Override
    public GuardedInvocation getGuardedInvocation(LinkRequest req, LinkerServices svc)
            throws Exception {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodType mt = req.getCallSiteDescriptor().getMethodType();
        Operation op = NamedOperation.getBaseOperation(req.getCallSiteDescriptor().getOperation());
        Object[] args = req.getArguments();

        GuardedInvocation result = null;

        if (NamespaceOperation.contains(op, RhinoOperation.ADD, RhinoNamespace.MATH)) {
            if (args[1] instanceof Integer) {
                result = new GuardedInvocation(
                        lookup.findStatic(IntegerMathLinker.class, "add", mt),
                        lookup.findStatic(IntegerMathLinker.class, "guardAdd",
                            MethodType.methodType(Boolean.TYPE, Object.class, Object.class, Context.class)));
            }
        } else if (NamespaceOperation.contains(op, RhinoOperation.BITWISE_AND, RhinoNamespace.MATH)) {
            if (args[1] instanceof Integer) {
                result = new GuardedInvocation(
                        lookup.findStatic(IntegerMathLinker.class, "bitwiseAND", mt),
                        getBitwiseGuard(lookup));
            }
        } else if (NamespaceOperation.contains(
                op, RhinoOperation.BITWISE_OR, RhinoNamespace.MATH)) {
            if (args[1] instanceof Integer) {
                result = new GuardedInvocation(
                        lookup.findStatic(IntegerMathLinker.class, "bitwiseOR", mt),
                        getBitwiseGuard(lookup));
            }
        } else if (NamespaceOperation.contains(
                op, RhinoOperation.BITWISE_XOR, RhinoNamespace.MATH)) {
            if (args[1] instanceof Integer) {
                result = new GuardedInvocation(
                        lookup.findStatic(IntegerMathLinker.class, "bitwiseXOR", mt),
                        getBitwiseGuard(lookup));
            }
        } else if (NamespaceOperation.contains(
                op, RhinoOperation.BITWISE_NOT, RhinoNamespace.MATH)) {
                result = new GuardedInvocation(
                    lookup.findStatic(IntegerMathLinker.class, "bitwiseNOT", mt),
                    Guards.getInstanceOfGuard(Integer.class));
        } else if (NamespaceOperation.contains(
                op, RhinoOperation.SIGNED_RIGHT_SHIFT, RhinoNamespace.MATH)) {
            if (args[1] instanceof Integer) {
                result = new GuardedInvocation(
                        lookup.findStatic(IntegerMathLinker.class, "signedRightShift", mt),
                        getBitwiseGuard(lookup));
            }
        } else if (NamespaceOperation.contains(
                op, RhinoOperation.LEFT_SHIFT, RhinoNamespace.MATH)) {
            if (args[1] instanceof Integer) {
                result = new GuardedInvocation(
                        lookup.findStatic(IntegerMathLinker.class, "leftShift", mt),
                        getBitwiseGuard(lookup));
            }
        }

        if (result != null && DefaultLinker.DEBUG) {
            System.out.println("Integer math link: " + op);
        }

        return result;
    }

    static MethodHandle getBitwiseGuard(MethodHandles.Lookup lookup)
            throws NoSuchMethodException, IllegalAccessException {
        return lookup.findStatic(
                IntegerMathLinker.class,
                "guardBitwise",
                MethodType.methodType(Boolean.TYPE, Number.class, Number.class));
    }

    static boolean guardBitwise(Number n1, Number n2) {
        return (n1 instanceof Integer) && (n2 instanceof Integer);
    }

    static Number bitwiseAND(Number n1, Number n2) {
        return Integer.valueOf(((Integer) n1).intValue() & ((Integer) n2).intValue());
    }

    static Number bitwiseOR(Number n1, Number n2) {
        return Integer.valueOf(((Integer) n1).intValue() | ((Integer) n2).intValue());
    }

    static Number bitwiseXOR(Number n1, Number n2) {
        return Integer.valueOf(((Integer) n1).intValue() ^ ((Integer) n2).intValue());
    }

    static Number bitwiseNOT(Number n) {
        return Integer.valueOf(~((Integer)n).intValue());
    }

    static Number leftShift(Number n1, Number n2) {
        return Integer.valueOf(((Integer) n1).intValue() << ((Integer) n2).intValue());
    }

    static Number signedRightShift(Number n1, Number n2) {
        return Integer.valueOf(((Integer) n1).intValue() >> ((Integer) n2).intValue());
    }

    static boolean guardAdd(Object o1, Object o2, Context cx) {
        return (o1 instanceof Integer) && (o2 instanceof Integer);
    }

    static Object add(Object o1, Object o2, Context cx) {
        return ScriptRuntime.add((Integer)o1, (Integer)o2);
    }
}
