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
            if (fastKey != null) {
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
            var fastKey = target.getFastPropertyKey(op.getName());
            if (fastKey != null) {
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
            if (fastKey != null) {
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
        }
        // TODO "and this" operations
        return null;
    }

    @SuppressWarnings("unused")
    private static boolean checkFastGet(
            ScriptableObject.FastKey key, Object target, Context cx, Scriptable scope) {
        if (isCompatibleScriptable(target.getClass())) {
            return ((ScriptableObject) target).validateFastPropertyKey(key);
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
        if (isCompatibleScriptable(target.getClass())) {
            return ((ScriptableObject) target).validateFastPropertyKey(key);
        }
        return false;
    }

    @SuppressWarnings("unused")
    private static boolean checkFastGetWithThis(
            ScriptableObject.FastKey key, Object target, Context cx, Scriptable scope) {
        if (isCompatibleScriptable(target.getClass())) {
            return ((ScriptableObject) target).validateFastPropertyKey(key);
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
        return so.putFastProperty(name, key, so, value, true);
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
}
