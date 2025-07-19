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
        String propertyName = op.getName();
        if (op.isNamespace(StandardNamespace.PROPERTY)
                && op.isOperation(StandardOperation.GET, RhinoOperation.GETNOWARN)) {
            // Look up property using fast path. Should work for direct properties
            // and also properties on the prototype.
            var fastKey = target.getFastKey(propertyName);
            if (fastKey != null) {
                if (DefaultLinker.DEBUG) {
                    System.out.println(op + "(" + target.getClass().getSimpleName() + "): fast property get");
                }
                return makeInvocation(
                        propertyName, lookup, req, fastKey, "checkFastGet", "getFast", false);
            }
        } else if (op.isNamespace(StandardNamespace.PROPERTY)
                && op.isOperation(RhinoOperation.GETWITHTHIS)) {
            // Same as above, but return the extra information needed when
            // we're about to call a function.
            var fastKey = target.getFastKey(propertyName);
            if (fastKey != null) {
                if (DefaultLinker.DEBUG) {
                    System.out.println(op + "(" + target.getClass().getSimpleName() + "): fast property get");
                }
                return makeInvocation(
                        propertyName,
                        lookup,
                        req,
                        fastKey,
                        "checkFastGet",
                        "getFastWithThis",
                        true);
            }
        } else if (op.isNamespace(StandardNamespace.PROPERTY)
                && op.isOperation(StandardOperation.SET)) {
            // Set using fast path if the property. Currently only for direct
            // object properties, not on the prototype.
            var fastKey = target.getFastWriteKey(op.getName(), 0);
            if (fastKey != null) {
                if (DefaultLinker.DEBUG) {
                    System.out.println(op + "(" + target.getClass().getSimpleName() + "): fast property set");
                }
                return makeInvocation(
                        propertyName, lookup, req, fastKey, "checkFastSet", "setFast", false);
            }
        }
        return null;
    }

    /**
     * Assume that "opMethod" is a method on this class with the same arguments as the target call,
     * with FastKey added as the first parameter, and that "guardMethod" has the same arguments as
     * "opMethod" but the return type is boolean. If "addPropertyName" is true, add the property
     * name as the first argument to the operation method.
     */
    private GuardedInvocation makeInvocation(
            String propertyName,
            MethodHandles.Lookup lookup,
            LinkRequest req,
            ScriptableObject.FastKey fastKey,
            String guardMethod,
            String opMethod,
            boolean addPropertyName)
            throws NoSuchMethodException, IllegalAccessException {
        MethodType mt =
                req.getCallSiteDescriptor()
                        .getMethodType()
                        .insertParameterTypes(0, ScriptableObject.FastKey.class);
        MethodType guardType = mt.changeReturnType(Boolean.TYPE);
        MethodHandle guard = lookup.findStatic(FastPropertyLinker.class, guardMethod, guardType);
        guard = MethodHandles.insertArguments(guard, 0, fastKey);
        if (addPropertyName) {
            mt = mt.insertParameterTypes(0, String.class);
        }
        MethodHandle get = lookup.findStatic(FastPropertyLinker.class, opMethod, mt);
        if (addPropertyName) {
            get = MethodHandles.insertArguments(get, 0, propertyName, fastKey);
        } else {
            get = MethodHandles.insertArguments(get, 0, fastKey);
        }
        return new GuardedInvocation(get, guard);
    }

    @SuppressWarnings("unused")
    private static boolean checkFastGet(
            ScriptableObject.FastKey key, Object target, Context cx, Scriptable scope) {
        if (target instanceof ScriptableObject) {
            return key.isCompatible((ScriptableObject) target);
        }
        return false;
    }

    @SuppressWarnings("unused")
    private static Object getFast(
            ScriptableObject.FastKey key, Object target, Context cx, Scriptable scope) {
        ScriptableObject so = (ScriptableObject) target;
        return so.getPropertyFast(key, so);
    }

    @SuppressWarnings("unused")
    private static ScriptRuntime.LookupResult getFastWithThis(
            String name,
            ScriptableObject.FastKey key,
            Object target,
            Context cx,
            Scriptable scope) {
        ScriptableObject so = (ScriptableObject) target;
        Object val = so.getPropertyFast(key, so);
        return new ScriptRuntime.LookupResult(val, so, name);
    }

    @SuppressWarnings("unused")
    private static boolean checkFastSet(
            ScriptableObject.FastKey key,
            Object target,
            Object value,
            Context cx,
            Scriptable scope) {
        if (target instanceof ScriptableObject) {
            return key.isCompatible((ScriptableObject) target);
        }
        return false;
    }

    @SuppressWarnings("unused")
    private static Object setFast(
            ScriptableObject.FastKey key,
            Object target,
            Object value,
            Context cx,
            Scriptable scope) {
        ScriptableObject so = (ScriptableObject) target;
        so.putPropertyFast(key, so, value, cx.isStrictMode());
        return value;
    }
}
