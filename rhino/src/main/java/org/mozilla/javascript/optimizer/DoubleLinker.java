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
 * This linker optimizes a suite of math operations when the LHS is a Double object, and the RHS (if
 * any) is either a Double or an Integer. It avoids a gigantic set of "if...then" statements in
 * ScriptRuntime for the generic case.
 */
@SuppressWarnings("AndroidJdkLibsChecker")
class DoubleLinker implements TypeBasedGuardingDynamicLinker {
    @Override
    public boolean canLinkType(Class<?> type) {
        return Double.class.equals(type);
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
                    && (arg2 instanceof Double || arg2 instanceof Integer)) {
                mh = lookup.findStatic(DoubleLinker.class, "addNum", mType);
                MethodType guardType = mType.changeReturnType(Boolean.TYPE);
                guard = lookup.findStatic(DoubleLinker.class, "testNum", guardType);
            } else if (op.isOperation(RhinoOperation.EQ, RhinoOperation.SHALLOWEQ)
                    && (arg2 instanceof Double || arg2 instanceof Integer)) {
                mh = lookup.findStatic(DoubleLinker.class, "eqNum", mType);
                guard = lookup.findStatic(DoubleLinker.class, "testTwoNum", mType);
            } else if (op.isOperation(RhinoOperation.COMPARE_LT)
                    && (arg2 instanceof Double || arg2 instanceof Integer)) {
                mh = lookup.findStatic(DoubleLinker.class, "compareLTNum", mType);
                guard = lookup.findStatic(DoubleLinker.class, "testTwoNum", mType);
            } else if (op.isOperation(RhinoOperation.COMPARE_GT)
                    && (arg2 instanceof Double || arg2 instanceof Integer)) {
                mh = lookup.findStatic(DoubleLinker.class, "compareGTNum", mType);
                guard = lookup.findStatic(DoubleLinker.class, "testTwoNum", mType);
            } else if (op.isOperation(RhinoOperation.COMPARE_LE)
                    && (arg2 instanceof Double || arg2 instanceof Integer)) {
                mh = lookup.findStatic(DoubleLinker.class, "compareLENum", mType);
                guard = lookup.findStatic(DoubleLinker.class, "testTwoNum", mType);
            } else if (op.isOperation(RhinoOperation.COMPARE_GE)
                    && (arg2 instanceof Double || arg2 instanceof Integer)) {
                mh = lookup.findStatic(DoubleLinker.class, "compareGENum", mType);
                guard = lookup.findStatic(DoubleLinker.class, "testTwoNum", mType);
            } else if (op.isOperation(RhinoOperation.TOBOOLEAN)) {
                mh = lookup.findStatic(DoubleLinker.class, "toBoolean", mType);
                guard = Guards.getInstanceOfGuard(Double.class);
            } else if (op.isOperation(RhinoOperation.TONUMBER)) {
                mh = lookup.findStatic(DoubleLinker.class, "toNumber", mType);
                guard = Guards.getInstanceOfGuard(Double.class);
            } else if (op.isOperation(RhinoOperation.TONUMERIC)) {
                mh = lookup.findStatic(DoubleLinker.class, "toNumeric", mType);
                guard = Guards.getInstanceOfGuard(Double.class);
            } else if (op.isOperation(RhinoOperation.TOINT32)) {
                mh = lookup.findStatic(DoubleLinker.class, "toInt32", mType);
                guard = Guards.getInstanceOfGuard(Double.class);
            } else if (op.isOperation(RhinoOperation.TOUINT32)) {
                mh = lookup.findStatic(DoubleLinker.class, "toUint32", mType);
                guard = Guards.getInstanceOfGuard(Double.class);
            }
        }

        if (mh != null) {
            assert guard != null;
            if (DefaultLinker.DEBUG) {
                System.out.println(op + " double operation");
            }
            return new GuardedInvocation(mh, guard);
        }

        return null;
    }

    /**
     * The RHS may be either a Double or an Integer. Both are accepted so that a call site whose
     * operand flips between the two representations does not need to be re-linked.
     */
    @SuppressWarnings("unused")
    private static boolean testNum(Object lval, Object rval, Context cx) {
        return lval instanceof Double && (rval instanceof Double || rval instanceof Integer);
    }

    @SuppressWarnings("unused")
    private static Object addNum(Object lval, Object rval, Context cx) {
        if (rval instanceof Integer) {
            return ((Double) lval) + ((Integer) rval);
        }
        return ((Double) lval) + ((Double) rval);
    }

    @SuppressWarnings("unused")
    private static boolean testTwoNum(Object lval, Object rval) {
        return lval instanceof Double && (rval instanceof Double || rval instanceof Integer);
    }

    @SuppressWarnings("unused")
    private static boolean eqNum(Object lval, Object rval) {
        return ((Double) lval).doubleValue() == ((Number) rval).doubleValue();
    }

    @SuppressWarnings("unused")
    private static boolean compareLTNum(Object lval, Object rval) {
        return ((Double) lval).doubleValue() < ((Number) rval).doubleValue();
    }

    @SuppressWarnings("unused")
    private static boolean compareGTNum(Object lval, Object rval) {
        return ((Double) lval).doubleValue() > ((Number) rval).doubleValue();
    }

    @SuppressWarnings("unused")
    private static boolean compareLENum(Object lval, Object rval) {
        return ((Double) lval).doubleValue() <= ((Number) rval).doubleValue();
    }

    @SuppressWarnings("unused")
    private static boolean compareGENum(Object lval, Object rval) {
        return ((Double) lval).doubleValue() >= ((Number) rval).doubleValue();
    }

    @SuppressWarnings("unused")
    private static double toNumber(Object raw) {
        return (Double) raw;
    }

    @SuppressWarnings("unused")
    private static Number toNumeric(Object raw) {
        return (Double) raw;
    }

    @SuppressWarnings("unused")
    private static boolean toBoolean(Object raw) {
        double v = (Double) raw;
        if (Double.isNaN(v)) {
            return false;
        }
        return v != 0.0;
    }

    @SuppressWarnings("unused")
    private static int toInt32(Object raw) {
        return ScriptRuntime.toInt32(((Double) raw).doubleValue());
    }

    @SuppressWarnings("unused")
    private static long toUint32(Object raw) {
        return ScriptRuntime.toUint32(((Double) raw).doubleValue());
    }
}
