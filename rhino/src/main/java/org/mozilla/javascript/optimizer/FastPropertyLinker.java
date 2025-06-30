package org.mozilla.javascript.optimizer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import jdk.dynalink.StandardNamespace;
import jdk.dynalink.StandardOperation;
import jdk.dynalink.linker.GuardedInvocation;
import jdk.dynalink.linker.LinkRequest;
import jdk.dynalink.linker.LinkerServices;
import jdk.dynalink.linker.TypeBasedGuardingDynamicLinker;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.WellBehavedProperties;

@SuppressWarnings("AndroidJdkLibsChecker")
class FastPropertyLinker implements TypeBasedGuardingDynamicLinker {
    @Override
    public boolean canLinkType(Class<?> type) {
        return isCompatibleScriptable(type);
    }

    private static boolean isCompatibleScriptable(Class<?> type) {
        // We can only link here if we know that the object will behave well,
        // and not override "get" and "set" in ways that make the fast property
        // optimization break down. The WellBehavedProperties annotation is
        // therefore only used on classes when we know that is the case.
        return ScriptableObject.class.isAssignableFrom(type)
                && type.isAnnotationPresent(WellBehavedProperties.class);
    }

    @Override
    public GuardedInvocation getGuardedInvocation(LinkRequest req, LinkerServices svc)
            throws Exception {
        if (req.isCallSiteUnstable()) {
            return null;
        }
        assert isCompatibleScriptable(req.getReceiver().getClass());

        MethodHandles.Lookup lookup = MethodHandles.lookup();
        ScriptableObject target = (ScriptableObject) req.getReceiver();
        ParsedOperation op = new ParsedOperation(req.getCallSiteDescriptor().getOperation());
        if (op.isNamespace(StandardNamespace.PROPERTY)
                && op.isOperation(StandardOperation.GET, RhinoOperation.GETNOWARN)) {
            var fastKey = target.getFastPropertyKey(op.getName());
            if (fastKey.isPresent()) {
                MethodType mt =
                        req.getCallSiteDescriptor()
                                .getMethodType()
                                .insertParameterTypes(0, ScriptableObject.FastKey.class);
                MethodType guardType = mt.changeReturnType(Boolean.TYPE);
                MethodHandle guard =
                        lookup.findStatic(FastPropertyLinker.class, "checkFastGet", guardType);
                guard = MethodHandles.insertArguments(guard, 0, fastKey);
                MethodHandle get = lookup.findStatic(FastPropertyLinker.class, "getFast", mt);
                get = MethodHandles.insertArguments(get, 0, fastKey);
                if (DefaultLinker.DEBUG) {
                    System.out.println(op + ": fast property get");
                }
                return new GuardedInvocation(get, guard);
            }
        } else if (op.isNamespace(StandardNamespace.PROPERTY)
                && op.isOperation(StandardOperation.SET)) {
            var fastKey = target.getWritableFastPropertyKey(op.getName(), 0);
            if (fastKey.isPresent()) {
                MethodType mt =
                        req.getCallSiteDescriptor()
                                .getMethodType()
                                .insertParameterTypes(0, ScriptableObject.FastKey.class);
                MethodType guardType = mt.changeReturnType(Boolean.TYPE);
                MethodHandle guard =
                        lookup.findStatic(FastPropertyLinker.class, "checkFastSet", guardType);
                mt = mt.insertParameterTypes(0, String.class);
                guard = MethodHandles.insertArguments(guard, 0, fastKey);
                MethodHandle get = lookup.findStatic(FastPropertyLinker.class, "setFast", mt);
                get = MethodHandles.insertArguments(get, 0, op.getName(), fastKey);
                if (DefaultLinker.DEBUG) {
                    System.out.println(op + ": fast property set");
                }
                return new GuardedInvocation(get, guard);
            }
        } else if (op.isNamespace(StandardNamespace.PROPERTY)
                && op.isOperation(RhinoOperation.GETWITHTHIS)) {
            var fastKey = target.getFastPropertyKey(op.getName());
            if (fastKey.isPresent()) {
                MethodType mt =
                        req.getCallSiteDescriptor()
                                .getMethodType()
                                .insertParameterTypes(0, ScriptableObject.FastKey.class);
                MethodType guardType = mt.changeReturnType(Boolean.TYPE);
                MethodHandle guard =
                        lookup.findStatic(
                                FastPropertyLinker.class, "checkFastGetWithThis", guardType);
                mt = mt.insertParameterTypes(0, String.class);
                guard = MethodHandles.insertArguments(guard, 0, fastKey);
                MethodHandle get =
                        lookup.findStatic(FastPropertyLinker.class, "getFastWithThis", mt);
                get = MethodHandles.insertArguments(get, 0, op.getName(), fastKey);
                if (DefaultLinker.DEBUG) {
                    System.out.println(op + ": fast property get");
                }
                return new GuardedInvocation(get, guard);
            }
            // Test again on the prototype
            if ((target.getPrototype() != null)
                    && isCompatibleScriptable(target.getPrototype().getClass())) {
                ScriptableObject proto = (ScriptableObject) target.getPrototype();
                var protoFastKey = proto.getFastPropertyKey(op.getName());
                if (protoFastKey.isPresent()) {
                    MethodType mt =
                            req.getCallSiteDescriptor()
                                    .getMethodType()
                                    .insertParameterTypes(
                                            0,
                                            ScriptableObject.FastKey.class,
                                            ScriptableObject.FastKey.class);
                    MethodType guardType = mt.changeReturnType(Boolean.TYPE);
                    MethodHandle guard =
                            lookup.findStatic(
                                    FastPropertyLinker.class,
                                    "checkFastGetWithThisPrototype",
                                    guardType);
                    mt = mt.insertParameterTypes(0, String.class);
                    guard = MethodHandles.insertArguments(guard, 0, fastKey, protoFastKey);
                    MethodHandle get =
                            lookup.findStatic(
                                    FastPropertyLinker.class, "getFastWithThisPrototype", mt);
                    get =
                            MethodHandles.insertArguments(
                                    get, 0, op.getName(), fastKey, protoFastKey);
                    if (DefaultLinker.DEBUG) {
                        System.out.println(op + ": fast prototype property get");
                    }
                    return new GuardedInvocation(get, guard);
                }
            }
        }

        /*
         * TODO new optimization:
         *    When adding a new property, if we're adding the same property to
         * the same shape, then we are always transitioning to the same new shape.
         * We can optimize that too, and it will speed up the splay benchmark.
         */

        return null;
    }

    @SuppressWarnings("unused")
    private static boolean checkFastGet(
            ScriptableObject.FastKey key, Object target, Context cx, Scriptable scope) {
        if (target instanceof ScriptableObject) {
            return key.isSameShape((ScriptableObject) target);
        }
        return false;
    }

    @SuppressWarnings("unused")
    private static boolean checkFastSet(
            ScriptableObject.FastKey key,
            Object target,
            Object value,
            Context cx,
            Scriptable scope) {
        if (target instanceof ScriptableObject) {
            return key.isSameShape((ScriptableObject) target);
        }
        return false;
    }

    @SuppressWarnings("unused")
    private static Object getFast(
            ScriptableObject.FastKey key, Object target, Context cx, Scriptable scope) {
        ScriptableObject so = (ScriptableObject) target;
        return so.getFastProperty(key, so);
    }

    @SuppressWarnings("unused")
    private static Object setFast(
            String name,
            ScriptableObject.FastKey key,
            Object target,
            Object value,
            Context cx,
            Scriptable scope) {
        ScriptableObject so = (ScriptableObject) target;
        so.putFastProperty(name, key, so, value, cx.isStrictMode());
        return value;
    }

    @SuppressWarnings("unused")
    private static boolean checkFastGetWithThis(
            ScriptableObject.FastKey key, Object target, Context cx, Scriptable scope) {
        if (target instanceof ScriptableObject) {
            return key.isSameShape((ScriptableObject) target);
        }
        return false;
    }

    @SuppressWarnings("unused")
    private static ScriptRuntime.LookupResult getFastWithThis(
            String name,
            ScriptableObject.FastKey key,
            Object target,
            Context cx,
            Scriptable scope) {
        ScriptableObject so = (ScriptableObject) target;
        Object val = so.getFastProperty(key, so);
        return new ScriptRuntime.LookupResult(val, so, name);
    }

    @SuppressWarnings("unused")
    private static boolean checkFastGetWithThisPrototype(
            ScriptableObject.FastKey key,
            ScriptableObject.FastKey prototypeKey,
            Object target,
            Context cx,
            Scriptable scope) {
        // Both the prototype and the target must be the same shape.
        // The prototype, because that's how the optimization works,
        // and the target, because a new property might have masked a
        // prototype property.
        if (target instanceof ScriptableObject) {
            ScriptableObject so = (ScriptableObject) target;
            if (key.isSameShape(so) && so.getPrototype() instanceof ScriptableObject) {
                return prototypeKey.isSameShape((ScriptableObject) so.getPrototype());
            }
        }
        return false;
    }

    @SuppressWarnings("unused")
    private static ScriptRuntime.LookupResult getFastWithThisPrototype(
            String name,
            ScriptableObject.FastKey key,
            ScriptableObject.FastKey prototypeKey,
            Object target,
            Context cx,
            Scriptable scope) {
        ScriptableObject so = (ScriptableObject) target;
        ScriptableObject proto = (ScriptableObject) so.getPrototype();
        Object val = proto.getFastProperty(prototypeKey, so);
        return new ScriptRuntime.LookupResult(val, so, name);
    }
}
