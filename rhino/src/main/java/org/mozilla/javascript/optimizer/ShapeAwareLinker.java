package org.mozilla.javascript.optimizer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Optional;
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
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Slot;
import org.mozilla.javascript.SlotMap;
import org.mozilla.javascript.SlotMap.FastQueryResult;

class ShapeAwareLinker implements GuardingDynamicLinker {

    @Override
    public GuardedInvocation getGuardedInvocation(LinkRequest req, LinkerServices svc)
            throws Exception {
        Object[] args = req.getArguments();
        if ((args == null) || (args.length < 1) || !(args[0] instanceof ScriptableObject)) {
            return null;
        }

        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Operation op = req.getCallSiteDescriptor().getOperation();
        String name = DefaultLinker.getName(op);
        op = NamedOperation.getBaseOperation(op);
        ScriptableObject target = (ScriptableObject) args[0];

        if (req.isCallSiteUnstable()) {
            if (DefaultLinker.DEBUG) {
                System.out.println("Unstable: " + op + ':' + name);
            }
            return null;
        }

        if (NamespaceOperation.contains(op, StandardOperation.GET, StandardNamespace.PROPERTY)
                || NamespaceOperation.contains(
                        op, RhinoOperation.GETNOWARN, StandardNamespace.PROPERTY)) {
            Optional<SlotMap.FastQueryResult> r = target.getSlotMap().queryFastIndex(name, 0);
            if (r.isPresent()) {
                return linkGetObjectProp(op, req, lookup, name, r.get());
            }

            /*
            } else if (NamespaceOperation.contains(op, RhinoOperation.GETWITHTHIS, StandardNamespace.PROPERTY)) {
            Optional<SlotMap.FastQueryResult> r = target.getSlotMap().queryFastIndex(name, 0);
                if (r.isPresent()) {
                    return linkGetObjectPropAndThis(op, req, lookup, name, r.get());
                }
            */

        } else if (NamespaceOperation.contains(
                op, StandardOperation.SET, StandardNamespace.PROPERTY)) {
            Optional<SlotMap.FastQueryResult> r = target.getSlotMap().queryFastIndex(name, 0);
            if (r.isPresent()) {
                return linkSetObjectProp(op, req, lookup, name, r.get());
            }

            /*
            } else if (NamespaceOperation.contains(op, StandardOperation.GET, RhinoNamespace.NAME)) {
                Context cx = (Context) args[1];
                if ((target.getParentScope() == null) && !cx.isUsingDynamicScope()) {
                    Optional<SlotMap.FastQueryResult> r = target.getSlotMap().queryFastIndex(name, 0);
                    if (r.isPresent()) {
                        return linkName(op, req, lookup, name, r.get());
                    }
                }
            */
        }

        // If we get here, fall through to the next linker in the chain
        return null;
    }

    /**
     * Equivalent code to ScriptRuntinme.getObjectProp(). Optimizes using a fast lookup if the
     * property is already present in the base object and not in the prototype chain.
     */
    private GuardedInvocation linkGetObjectProp(
            Operation op,
            LinkRequest req,
            MethodHandles.Lookup lookup,
            String name,
            FastQueryResult r)
            throws NoSuchMethodException, IllegalAccessException {
        MethodType guardType =
                req.getCallSiteDescriptor()
                        .getMethodType()
                        .changeReturnType(Boolean.TYPE)
                        .insertParameterTypes(3, String.class, SlotMap.FastTester.class);
        MethodHandle guardHandle =
                lookup.findStatic(ShapeAwareLinker.class, "isFastIndexValidForRead", guardType);
        guardHandle = MethodHandles.insertArguments(guardHandle, 3, name, r.getDiscriminator());

        MethodType callType =
                req.getCallSiteDescriptor().getMethodType().insertParameterTypes(3, Integer.TYPE);
        MethodHandle callHandle =
                lookup.findStatic(ShapeAwareLinker.class, "queryFastIndex", callType);
        callHandle = MethodHandles.insertArguments(callHandle, 3, r.getIndex());

        if (DefaultLinker.DEBUG) {
            System.out.println("Fast link: " + op + ':' + name + " index " + r.getIndex());
        }

        return new GuardedInvocation(callHandle, guardHandle);
    }

    /**
     * The guard for a fast property index. If it returns true, then we know that the given property
     * can be found at the specified index.
     */
    static boolean isFastIndexValidForRead(
            Object t, Context cx, Scriptable scope, String key, SlotMap.FastTester tester) {
        if (!(t instanceof ScriptableObject)) {
            return false;
        }

        ScriptableObject target = (ScriptableObject) t;
        return tester.test(target.getSlotMap(), key, 0);
    }

    /** The method to read a property quickly. */
    static Object queryFastIndex(Object t, Context cx, Scriptable scope, int fastIndex) {
        ScriptableObject target = (ScriptableObject) t;
        Slot slot = target.getSlotMap().queryFast(fastIndex);
        return slot.getValue(target);
    }

    /**
     * Equivalent code to ScriptRuntinme.getPropFunctionAndThis(). Optimizes using a fast lookup if
     * the property is already present in the base object and not in the prototype chain.
     */
    /*
    private GuardedInvocation linkGetObjectPropAndThis(Operation op, LinkRequest req,
        MethodHandles.Lookup lookup, String name, FastQueryResult r) throws NoSuchMethodException, IllegalAccessException {
        MethodType guardType =
                req.getCallSiteDescriptor()
                        .getMethodType()
                        .changeReturnType(Boolean.TYPE)
                        .insertParameterTypes(3, String.class, SlotMap.FastTester.class);
        MethodHandle guardHandle =
                lookup.findStatic(
                        ShapeAwareLinker.class, "isFastIndexValidForRead", guardType);
        guardHandle =
                MethodHandles.insertArguments(
                        guardHandle, 3, name, r.getDiscriminator());

        MethodType callType =
                req.getCallSiteDescriptor()
                        .getMethodType()
                        .insertParameterTypes(3, Integer.TYPE, String.class);
        MethodHandle callHandle =
                lookup.findStatic(ShapeAwareLinker.class, "queryFastIndexAndThis", callType);
        callHandle = MethodHandles.insertArguments(callHandle, 3, r.getIndex(), name);

        if (DefaultLinker.DEBUG) {
                System.out.println(
                        "Fast link: " + op + ':' + name + " index " + r.getIndex());
        }

        return new GuardedInvocation(callHandle, guardHandle);
    }
    */

    static Callable queryFastIndexAndThis(
            Object t, Context cx, Scriptable scope, int fastIndex, String name) {
        ScriptableObject target = (ScriptableObject) t;
        Slot slot = target.getSlotMap().queryFast(fastIndex);
        Object value = slot.getValue(target);
        if (value instanceof Callable) {
            ScriptRuntime.storeScriptable(cx, scope);
            return (Callable) value;
        }
        throw ScriptRuntime.notFunctionError(scope, value, name);
    }

    /**
     * Equivalent code to ScriptRuntime.setObjectProp(), for properties in the base object that are
     * already present at link time.
     */
    private GuardedInvocation linkSetObjectProp(
            Operation op,
            LinkRequest req,
            MethodHandles.Lookup lookup,
            String name,
            FastQueryResult r)
            throws NoSuchMethodException, IllegalAccessException {
        MethodType guardType =
                req.getCallSiteDescriptor()
                        .getMethodType()
                        .changeReturnType(Boolean.TYPE)
                        .insertParameterTypes(4, String.class, SlotMap.FastTester.class);
        MethodHandle guardHandle =
                lookup.findStatic(ShapeAwareLinker.class, "isFastIndexValidForWrite", guardType);
        guardHandle = MethodHandles.insertArguments(guardHandle, 4, name, r.getDiscriminator());
        MethodType callType =
                req.getCallSiteDescriptor().getMethodType().insertParameterTypes(4, Integer.TYPE);
        MethodHandle callHandle =
                lookup.findStatic(ShapeAwareLinker.class, "setFastIndex", callType);
        callHandle = MethodHandles.insertArguments(callHandle, 4, r.getIndex());

        if (DefaultLinker.DEBUG) {
            System.out.println("Fast link: " + op + ':' + name + " index " + r.getIndex());
        }

        return new GuardedInvocation(callHandle, guardHandle);
    }

    static boolean isFastIndexValidForWrite(
            Object t,
            Object v,
            Context cx,
            Scriptable scope,
            String key,
            SlotMap.FastTester tester) {
        if (!(t instanceof ScriptableObject)) {
            return false;
        }
        ScriptableObject target = (ScriptableObject) t;
        return tester.test(target.getSlotMap(), key, 0);
    }

    /** And the method to set it quickly, since we know that it is already present. */
    static Object setFastIndex(
            Object t, Object value, Context cx, Scriptable scope, int fastIndex) {
        ScriptableObject target = (ScriptableObject) t;
        Slot slot = target.getSlotMap().queryFast(fastIndex);
        slot.setValue(value, target, target, cx.isStrictMode());
        return value;
    }

    /**
     * Equivalent code to ScriptRuntinme.name(). Optimizes using a fast lookup if there is no parent
     * scope and the property already exists inthe base object.
     */
    /*
    private GuardedInvocation linkName(Operation op, LinkRequest req,
        MethodHandles.Lookup lookup, String name, FastQueryResult r) throws NoSuchMethodException, IllegalAccessException {
        MethodType guardType =
                req.getCallSiteDescriptor()
                        .getMethodType()
                        .changeReturnType(Boolean.TYPE)
                        .insertParameterTypes(2, String.class, SlotMap.FastTester.class);
        MethodHandle guardHandle =
                lookup.findStatic(
                        ShapeAwareLinker.class, "isFastIndexValidForNameRead", guardType);
        guardHandle =
                MethodHandles.insertArguments(
                        guardHandle, 2, name, r.getDiscriminator());

        MethodType callType =
                req.getCallSiteDescriptor()
                        .getMethodType()
                        .insertParameterTypes(2, Integer.TYPE);
        MethodHandle callHandle =
                lookup.findStatic(ShapeAwareLinker.class, "queryNameFastIndex", callType);
        callHandle = MethodHandles.insertArguments(callHandle, 2, r.getIndex());

        if (DefaultLinker.DEBUG) {
                System.out.println(
                        "Fast link: " + op + ':' + name + " index " + r.getIndex());
        }

        return new GuardedInvocation(callHandle, guardHandle);
    }
    */

    static boolean isFastIndexValidForNameRead(
            Scriptable t, Context cx, String name, SlotMap.FastTester tester) {
        if (!(t instanceof ScriptableObject)) {
            return false;
        }
        ScriptableObject target = (ScriptableObject) t;
        if ((target.getParentScope() != null) || cx.isUsingDynamicScope()) {
            return false;
        }
        return tester.test(target.getSlotMap(), name, 0);
    }

    static Object queryNameFastIndex(Scriptable t, Context cx, int fastIndex) {
        ScriptableObject target = (ScriptableObject) t;
        Slot slot = target.getSlotMap().queryFast(fastIndex);
        return slot.getValue(target);
    }

    /*
    private GuardedInvocation linkNameAndThis(Operation op, LinkRequest req,
        MethodHandles.Lookup lookup, String name, FastQueryResult r) throws NoSuchMethodException, IllegalAccessException {
        MethodType guardType =
                req.getCallSiteDescriptor()
                        .getMethodType()
                        .changeReturnType(Boolean.TYPE)
                        .insertParameterTypes(2, String.class, SlotMap.FastTester.class);
        MethodHandle guardHandle =
                lookup.findStatic(
                        ShapeAwareLinker.class, "isFastIndexValidForNameRead", guardType);
        guardHandle =
                MethodHandles.insertArguments(
                        guardHandle, 2, name, r.getDiscriminator());

        MethodType callType =
                req.getCallSiteDescriptor()
                        .getMethodType()
                        .insertParameterTypes(2, Integer.TYPE, String.class);
        MethodHandle callHandle =
                lookup.findStatic(ShapeAwareLinker.class, "queryNameAndThisFastIndex", callType);
        callHandle = MethodHandles.insertArguments(callHandle, 2, r.getIndex(), name);

        if (DefaultLinker.DEBUG) {
                System.out.println(
                        "Fast link: " + op + ':' + name + " index " + r.getIndex());
        }

        return new GuardedInvocation(callHandle, guardHandle);
    }
    */

    static Object queryNameAndThisFastIndex(Scriptable t, Context cx, int fastIndex, String name) {
        ScriptableObject target = (ScriptableObject) t;
        Slot slot = target.getSlotMap().queryFast(fastIndex);
        Object value = slot.getValue(target);
        if (value instanceof Callable) {
            ScriptRuntime.storeScriptable(cx, target);
            return (Callable) value;
        }
        throw ScriptRuntime.notFunctionError(target, value, name);
    }
}
