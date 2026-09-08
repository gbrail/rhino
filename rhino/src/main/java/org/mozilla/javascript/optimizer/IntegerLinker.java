package org.mozilla.javascript.optimizer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import jdk.dynalink.linker.GuardedInvocation;
import jdk.dynalink.linker.LinkRequest;
import jdk.dynalink.linker.LinkerServices;
import jdk.dynalink.linker.TypeBasedGuardingDynamicLinker;
import jdk.dynalink.linker.support.Guards;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptRuntime;

/**
 * This linker optimizes a suite of math operations when the LHS is an Integer object, and the RHS
 * (if any) is either an Integer or a Double. It avoids a gigantic set of "if...then" statements in
 * ScriptRuntime for the generic case. When operating on Integers (and there is no overflow) it and
 * ScriptRuntime contrive to return Integer results, which can result in faster operations later.
 */
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

        ParsedOperation op = new ParsedOperation(req.getCallSiteDescriptor().getOperation());
        MethodHandle mh = null;
        MethodHandle guard = null;

        if (op.isNamespace(RhinoNamespace.MATH)) {
            Object arg2 = null;
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodType mType = req.getCallSiteDescriptor().getMethodType();

            if (req.getArguments().length > 1) {
                arg2 = req.getArguments()[1];
            }
            if (op.isOperation(RhinoOperation.ADD)
                    && (arg2 instanceof Integer || arg2 instanceof Double)) {
                mh = lookup.findStatic(IntegerLinker.class, "addNum", mType);
                MethodType guardType = mType.changeReturnType(Boolean.TYPE);
                guard = lookup.findStatic(IntegerLinker.class, "testNum", guardType);
            } else if (op.isOperation(RhinoOperation.EQ, RhinoOperation.SHALLOWEQ)
                    && (arg2 instanceof Integer || arg2 instanceof Double)) {
                mh = lookup.findStatic(IntegerLinker.class, "eqNum", mType);
                guard = lookup.findStatic(IntegerLinker.class, "testTwoNum", mType);
            } else if (op.isOperation(RhinoOperation.COMPARE_LT)
                    && (arg2 instanceof Integer || arg2 instanceof Double)) {
                mh = lookup.findStatic(IntegerLinker.class, "compareLTNum", mType);
                guard = lookup.findStatic(IntegerLinker.class, "testTwoNum", mType);
            } else if (op.isOperation(RhinoOperation.COMPARE_GT)
                    && (arg2 instanceof Integer || arg2 instanceof Double)) {
                mh = lookup.findStatic(IntegerLinker.class, "compareGTNum", mType);
                guard = lookup.findStatic(IntegerLinker.class, "testTwoNum", mType);
            } else if (op.isOperation(RhinoOperation.COMPARE_LE)
                    && (arg2 instanceof Integer || arg2 instanceof Double)) {
                mh = lookup.findStatic(IntegerLinker.class, "compareLENum", mType);
                guard = lookup.findStatic(IntegerLinker.class, "testTwoNum", mType);
            } else if (op.isOperation(RhinoOperation.COMPARE_GE)
                    && (arg2 instanceof Integer || arg2 instanceof Double)) {
                mh = lookup.findStatic(IntegerLinker.class, "compareGENum", mType);
                guard = lookup.findStatic(IntegerLinker.class, "testTwoNum", mType);
            } else if (op.isOperation(RhinoOperation.TOBOOLEAN)) {
                mh = lookup.findStatic(IntegerLinker.class, "toBoolean", mType);
                guard = Guards.getInstanceOfGuard(Integer.class);
            } else if (op.isOperation(RhinoOperation.TONUMBER)) {
                mh = lookup.findStatic(IntegerLinker.class, "toNumber", mType);
                guard = Guards.getInstanceOfGuard(Integer.class);
            } else if (op.isOperation(RhinoOperation.TONUMERIC)) {
                mh = lookup.findStatic(IntegerLinker.class, "toNumeric", mType);
                guard = Guards.getInstanceOfGuard(Integer.class);
            } else if (op.isOperation(RhinoOperation.TOINT32)) {
                mh = lookup.findStatic(IntegerLinker.class, "toInt32", mType);
                guard = Guards.getInstanceOfGuard(Integer.class);
            } else if (op.isOperation(RhinoOperation.TOUINT32)) {
                mh = lookup.findStatic(IntegerLinker.class, "toUint32", mType);
                guard = Guards.getInstanceOfGuard(Integer.class);
            }
        }

        if (mh != null) {
            assert guard != null;
            if (DefaultLinker.DEBUG) {
                System.out.println(op + " integer operation");
            }
            return new GuardedInvocation(mh, guard);
        }

        return null;
    }

    /**
     * The RHS may be either an Integer or a Double. Both are accepted so that a call site whose
     * operand flips between the two representations does not need to be re-linked.
     */
    @SuppressWarnings("unused")
    private static boolean testNum(Object lval, Object rval, Context cx) {
        return lval instanceof Integer && (rval instanceof Integer || rval instanceof Double);
    }

    @SuppressWarnings("unused")
    private static Object addNum(Object lval, Object rval, Context cx) {
        if (rval instanceof Integer) {
            return ScriptRuntime.add((Integer) lval, (Integer) rval);
        }
        return ((Integer) lval).doubleValue() + ((Double) rval).doubleValue();
    }

    @SuppressWarnings("unused")
    private static boolean testTwoNum(Object lval, Object rval) {
        return lval instanceof Integer && (rval instanceof Integer || rval instanceof Double);
    }

    @SuppressWarnings("unused")
    private static boolean eqNum(Object lval, Object rval) {
        return ((Integer) lval).doubleValue() == ((Number) rval).doubleValue();
    }

    @SuppressWarnings("unused")
    private static boolean compareLTNum(Object lval, Object rval) {
        return ((Integer) lval).doubleValue() < ((Number) rval).doubleValue();
    }

    @SuppressWarnings("unused")
    private static boolean compareGTNum(Object lval, Object rval) {
        return ((Integer) lval).doubleValue() > ((Number) rval).doubleValue();
    }

    @SuppressWarnings("unused")
    private static boolean compareLENum(Object lval, Object rval) {
        return ((Integer) lval).doubleValue() <= ((Number) rval).doubleValue();
    }

    @SuppressWarnings("unused")
    private static boolean compareGENum(Object lval, Object rval) {
        return ((Integer) lval).doubleValue() >= ((Number) rval).doubleValue();
    }

    @SuppressWarnings("unused")
    private static double toNumber(Object raw) {
        return ((Integer) raw).doubleValue();
    }

    @SuppressWarnings("unused")
    private static Number toNumeric(Object raw) {
        return (Number) raw;
    }

    @SuppressWarnings("unused")
    private static boolean toBoolean(Object raw) {
        return ((Integer) raw) != 0;
    }

    @SuppressWarnings("unused")
    private static int toInt32(Object raw) {
        return ((Integer) raw);
    }

    @SuppressWarnings("unused")
    private static long toUint32(Object raw) {
        return Integer.toUnsignedLong((Integer) raw);
    }
}
