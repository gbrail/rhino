package org.mozilla.javascript.optimizer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Objects;
import java.util.OptionalInt;
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
import org.mozilla.javascript.ObjectShape;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Slot;

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

        if (NamespaceOperation.contains(op, StandardOperation.GET, StandardNamespace.PROPERTY)
                || NamespaceOperation.contains(
                        op, RhinoOperation.GETNOWARN, StandardNamespace.PROPERTY)) {
            OptionalInt fastIndex = target.getSlotMap().queryFastIndex(name, 0);
            if (fastIndex.isPresent()) {
                // Off to the races! The target has a shape-aware slot map and we know where to go
                // to fetch the object rather quickly.
                MethodType guardType =
                        req.getCallSiteDescriptor()
                                .getMethodType()
                                .changeReturnType(Boolean.TYPE)
                                .insertParameterTypes(3, ObjectShape.class);
                MethodHandle guardHandle =
                        lookup.findStatic(
                                ShapeAwareLinker.class, "isFastIndexValidForRead", guardType);
                guardHandle =
                        MethodHandles.insertArguments(
                                guardHandle, 3, target.getSlotMap().getShape());

                MethodType callType =
                        req.getCallSiteDescriptor()
                                .getMethodType()
                                .insertParameterTypes(3, Integer.TYPE);
                MethodHandle callHandle =
                        lookup.findStatic(ShapeAwareLinker.class, "queryFastIndex", callType);
                callHandle = MethodHandles.insertArguments(callHandle, 3, fastIndex.getAsInt());

                if (DefaultLinker.DEBUG) {
                    System.out.println(
                            "Fast link: " + op + ':' + name + " index " + fastIndex.getAsInt());
                }

                return new GuardedInvocation(callHandle, guardHandle);
            }

        } else if (NamespaceOperation.contains(
                op, StandardOperation.SET, StandardNamespace.PROPERTY)) {
            OptionalInt fastIndex = target.getSlotMap().queryFastIndex(name, 0);
            if (fastIndex.isPresent()) {
                // The logic here is like for get, because we are optimizing for the case of setting
                // a property that we know already exists.
                MethodType guardType =
                        req.getCallSiteDescriptor()
                                .getMethodType()
                                .changeReturnType(Boolean.TYPE)
                                .insertParameterTypes(4, ObjectShape.class);
                MethodHandle guardHandle =
                        lookup.findStatic(
                                ShapeAwareLinker.class, "isFastIndexValidForWrite", guardType);
                guardHandle =
                        MethodHandles.insertArguments(
                                guardHandle, 4, target.getSlotMap().getShape());

                MethodType callType =
                        req.getCallSiteDescriptor()
                                .getMethodType()
                                .insertParameterTypes(4, Integer.TYPE);
                MethodHandle callHandle =
                        lookup.findStatic(ShapeAwareLinker.class, "setFastIndex", callType);
                callHandle = MethodHandles.insertArguments(callHandle, 4, fastIndex.getAsInt());

                if (DefaultLinker.DEBUG) {
                    System.out.println(
                            "Fast link: " + op + ':' + name + " index " + fastIndex.getAsInt());
                }

                return new GuardedInvocation(callHandle, guardHandle);

                /*
                 * This next bit tries to assume the property map. The problem is that we don't
                 * know that unless we try an insert first, since there are 10 different ways that
                 * new properties don't end up in the slot map.
                } else if (target.getSlotMap() != null) {
                    // We know that the property does not exist, but we know how to insert it quickly
                    // with a pre-cached object shape table.
                    ObjectShape shapeBefore = target.getSlotMap().getShape();
                    ObjectShape.Result r = shapeBefore.putProperty(name);
                    assert r.getShape().isPresent();
                    ObjectShape shapeAfter = r.getShape().get();

                    // Guard handle is the same as for read
                    MethodType guardType =
                            req.getCallSiteDescriptor()
                                    .getMethodType()
                                    .changeReturnType(Boolean.TYPE)
                                    .insertParameterTypes(4, ObjectShape.class);
                    MethodHandle guardHandle =
                            lookup.findStatic(
                                    ShapeAwareLinker.class, "isFastIndexValidForWrite", guardType);
                    guardHandle = MethodHandles.insertArguments(guardHandle, 4, shapeBefore);

                    // Write handle needs to pass the new shape table
                    MethodType callType =
                            req.getCallSiteDescriptor()
                                    .getMethodType()
                                    .insertParameterTypes(4, String.class)
                                    .insertParameterTypes(5, ObjectShape.class);
                    MethodHandle callHandle =
                            lookup.findStatic(ShapeAwareLinker.class, "addNewFastIndex", callType);
                    callHandle = MethodHandles.insertArguments(callHandle, 4, name, shapeAfter);

                    if (DefaultLinker.DEBUG) {
                        System.out.println("Fast link: " + op + ':' + name);
                    }

                    return new GuardedInvocation(callHandle, guardHandle);
                */
            }

        } else if (NamespaceOperation.contains(op, StandardOperation.GET, RhinoNamespace.NAME)
                && (target.getParentScope() == null)) {
            OptionalInt fastIndex = target.getSlotMap().queryFastIndex(name, 0);
            if (fastIndex.isPresent()) {
                // Same basic algorithm as getting a property, in that we get the index
                // and use it later for fast access.
                MethodType guardType =
                        req.getCallSiteDescriptor()
                                .getMethodType()
                                .changeReturnType(Boolean.TYPE)
                                .insertParameterTypes(2, ObjectShape.class);
                MethodHandle guardHandle =
                        lookup.findStatic(
                                ShapeAwareLinker.class, "isFastIndexValidForNameRead", guardType);
                guardHandle =
                        MethodHandles.insertArguments(
                                guardHandle, 2, target.getSlotMap().getShape());

                MethodType callType =
                        req.getCallSiteDescriptor()
                                .getMethodType()
                                .insertParameterTypes(2, Integer.TYPE);
                MethodHandle callHandle =
                        lookup.findStatic(ShapeAwareLinker.class, "queryNameFastIndex", callType);
                callHandle = MethodHandles.insertArguments(callHandle, 2, fastIndex.getAsInt());

                if (DefaultLinker.DEBUG) {
                    System.out.println(
                            "Fast link: " + op + ':' + name + " index " + fastIndex.getAsInt());
                }

                return new GuardedInvocation(callHandle, guardHandle);
            }

        } else if (NamespaceOperation.contains(op, StandardOperation.SET, RhinoNamespace.NAME)
                || NamespaceOperation.contains(op, RhinoOperation.SETSTRICT, RhinoNamespace.NAME)) {
            OptionalInt fastIndex = target.getSlotMap().queryFastIndex(name, 0);
            if (fastIndex.isPresent()) {
                // Same basic algorithm as getting a property, in that we get the index
                // and use it later for fast access.
                MethodType guardType =
                        req.getCallSiteDescriptor()
                                .getMethodType()
                                .changeReturnType(Boolean.TYPE)
                                .insertParameterTypes(4, ObjectShape.class);
                MethodHandle guardHandle =
                        lookup.findStatic(
                                ShapeAwareLinker.class, "isFastIndexValidForNameWrite", guardType);
                guardHandle =
                        MethodHandles.insertArguments(
                                guardHandle, 4, target.getSlotMap().getShape());

                MethodType callType =
                        req.getCallSiteDescriptor()
                                .getMethodType()
                                .insertParameterTypes(4, Integer.TYPE);
                MethodHandle callHandle =
                        lookup.findStatic(ShapeAwareLinker.class, "setNameFastIndex", callType);
                callHandle = MethodHandles.insertArguments(callHandle, 4, fastIndex.getAsInt());

                if (DefaultLinker.DEBUG) {
                    System.out.println(
                            "Fast link: " + op + ':' + name + " index " + fastIndex.getAsInt());
                }

                return new GuardedInvocation(callHandle, guardHandle);
            }
        }

        // If we get here, fall through to the next linker in the chain
        return null;
    }

    /**
     * The guard for a fast property index. If it returns true, then we know that the given property
     * can be found at the specified index.
     */
    @SuppressWarnings("unused")
    private static boolean isFastIndexValidForRead(
            Object t, Context cx, Scriptable scope, ObjectShape shape) {
        if (!(t instanceof ScriptableObject)) {
            return false;
        }
        ScriptableObject target = (ScriptableObject) t;
        return Objects.equals(shape, target.getSlotMap().getShape());
    }

    @SuppressWarnings("unused")
    private static boolean isFastIndexValidForNameRead(
            Scriptable t, Context cx, ObjectShape shape) {
        if (!(t instanceof ScriptableObject)) {
            return false;
        }
        ScriptableObject target = (ScriptableObject) t;
        if (target.getParentScope() != null) {
            return false;
        }
        return Objects.equals(shape, target.getSlotMap().getShape());
    }

    @SuppressWarnings("unused")
    private static boolean isFastIndexValidForWrite(
            Object t, Object v, Context cx, Scriptable scope, ObjectShape shape) {
        if (!(t instanceof ScriptableObject)) {
            return false;
        }
        ScriptableObject target = (ScriptableObject) t;
        return Objects.equals(shape, target.getSlotMap().getShape());
    }

    @SuppressWarnings("unused")
    private static boolean isFastIndexValidForNameWrite(
            Scriptable t, Object v, Context cx, Scriptable scope, ObjectShape shape) {
        if (!(t instanceof ScriptableObject)) {
            return false;
        }
        ScriptableObject target = (ScriptableObject) t;
        return Objects.equals(shape, target.getSlotMap().getShape());
    }

    /** The method to read a property quickly. */
    @SuppressWarnings("unused")
    private static Object queryFastIndex(Object t, Context cx, Scriptable scope, int fastIndex) {
        ScriptableObject target = (ScriptableObject) t;
        Slot slot = target.getSlotMap().queryFast(fastIndex);
        return slot.getValue(target);
    }

    @SuppressWarnings("unused")
    private static Object queryNameFastIndex(Scriptable t, Context cx, int fastIndex) {
        ScriptableObject target = (ScriptableObject) t;
        Slot slot = target.getSlotMap().queryFast(fastIndex);
        return slot.getValue(target);
    }

    /** And the method to set it quickly, since we know that it is already present. */
    @SuppressWarnings("unused")
    private static Object setFastIndex(
            Object t, Object value, Context cx, Scriptable scope, int fastIndex) {
        ScriptableObject target = (ScriptableObject) t;
        Slot slot = target.getSlotMap().queryFast(fastIndex);
        return slot.setValue(value, target, target, cx.isStrictMode());
    }

    @SuppressWarnings("unused")
    private static Object setNameFastIndex(
            Scriptable t, Object value, Context cx, Scriptable scope, int fastIndex) {
        ScriptableObject target = (ScriptableObject) t;
        Slot slot = target.getSlotMap().queryFast(fastIndex);
        return slot.setValue(value, target, target, cx.isStrictMode());
    }

    /**
     * This method assumes that we know the current object shape, and what the new shape will be,
     * and we just need to append a slot to the end.
     */
    @SuppressWarnings("unused")
    private static Object addNewFastIndex(
            Object t,
            Object value,
            Context cx,
            Scriptable scope,
            String name,
            ObjectShape newShape) {
        ScriptableObject target = (ScriptableObject) t;
        Slot slot = target.getSlotMap().addFast(name, 0, newShape);
        return slot.setValue(value, target, target, cx.isStrictMode());
    }
}
