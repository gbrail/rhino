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
import jdk.dynalink.linker.support.Guards;
import org.mozilla.javascript.Callable;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.ScriptRuntime.LookupResult;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.SlotMapOwner;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.VarScope;

/**
 * Optimizes named property reads on objects backed by a slot map (every {@code ScriptableObject},
 * via {@link SlotMapOwner}) by querying the owner's own slot map directly instead of going through
 * the generic prototype-chain walk in {@code ScriptableObject.getProperty}. The common case -- an
 * own data property on a plain object -- resolves in a single hash probe and a field load, and the
 * direct call lets the JIT inline the lookup. When the property is not an own property the lookup
 * falls back to the full script runtime path, so semantics are unchanged.
 */
@SuppressWarnings("AndroidJdkLibsChecker")
class SlotMapLinker implements TypeBasedGuardingDynamicLinker {
    @Override
    public boolean canLinkType(Class<?> type) {
        // Scope objects must keep the variable-lookup semantics that
        // ScriptRuntime.getObjectProp implements for VarScope receivers.
        return SlotMapOwner.class.isAssignableFrom(type) && !VarScope.class.isAssignableFrom(type);
    }

    @Override
    public GuardedInvocation getGuardedInvocation(LinkRequest req, LinkerServices svc)
            throws NoSuchMethodException, IllegalAccessException {
        if (req.isCallSiteUnstable()) {
            return null;
        }

        ParsedOperation op = new ParsedOperation(req.getCallSiteDescriptor().getOperation());
        if (!op.isNamespace(StandardNamespace.PROPERTY)) {
            return null;
        }

        boolean isGet = op.isOperation(StandardOperation.GET);
        boolean isGetNoWarn = op.isOperation(RhinoOperation.GETNOWARN);
        boolean isGetWithThis = op.isOperation(RhinoOperation.GETWITHTHIS);
        boolean isGetWithThisOptional = op.isOperation(RhinoOperation.GETWITHTHISOPTIONAL);
        if (!(isGet || isGetNoWarn || isGetWithThis || isGetWithThisOptional)) {
            return null;
        }

        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodType mType = req.getCallSiteDescriptor().getMethodType();
        String method;
        if (isGetWithThis) {
            method = "getPropAndThis";
        } else if (isGetWithThisOptional) {
            method = "getPropAndThisOptional";
        } else if (isGetNoWarn) {
            method = "getPropNoWarn";
        } else {
            method = "getProp";
        }
        MethodType actualType = mType.insertParameterTypes(1, String.class);
        MethodHandle mh = lookup.findStatic(SlotMapLinker.class, method, actualType);
        mh = MethodHandles.insertArguments(mh, 1, op.getName());
        if (DefaultLinker.DEBUG) {
            System.out.println(op + " slot map get");
        }
        return new GuardedInvocation(mh, Guards.getInstanceOfGuard(SlotMapOwner.class));
    }

    /**
     * Reads a named property directly from the owner's slot map, then walks the prototype chain
     * without re-querying the owner's own map. The name parameter is bound from the call site
     * descriptor before this method is invoked.
     *
     * <p>This and the other target methods below are referenced by name via {@code
     * MethodHandles.findStatic} in {@link #getGuardedInvocation}, so the "unused" suppression is
     * required.
     */
    @SuppressWarnings({"unused", "unchecked"})
    private static Object getProp(Object obj, String name, Context cx, VarScope scope) {
        if (obj instanceof VarScope) {
            return ScriptRuntime.getObjectProp(obj, name, cx, scope);
        }
        SlotMapOwner<Scriptable> owner = (SlotMapOwner<Scriptable>) obj;
        Object value = owner.get(name, (Scriptable) obj);
        Scriptable proto = ((Scriptable) obj).getPrototype();
        while (proto != null && value == Scriptable.NOT_FOUND) {
            value = proto.get(name, (Scriptable) obj);
            proto = proto.getPrototype();
        }
        if (value == Scriptable.NOT_FOUND) {
            if (cx.hasFeature(Context.FEATURE_STRICT_MODE)) {
                Context.reportWarning(ScriptRuntime.getMessageById("msg.ref.undefined.prop", name));
            }
            return Undefined.instance;
        }
        return value;
    }

    @SuppressWarnings({"unused", "unchecked"})
    private static Object getPropNoWarn(Object obj, String name, Context cx, VarScope scope) {
        if (obj instanceof VarScope) {
            return ScriptRuntime.getObjectPropNoWarn(obj, name, cx, scope);
        }
        SlotMapOwner<Scriptable> owner = (SlotMapOwner<Scriptable>) obj;
        Object value = owner.get(name, (Scriptable) obj);
        Scriptable proto = ((Scriptable) obj).getPrototype();
        while (proto != null && value == Scriptable.NOT_FOUND) {
            value = proto.get(name, (Scriptable) obj);
            proto = proto.getPrototype();
        }
        if (value == Scriptable.NOT_FOUND) {
            return Undefined.instance;
        }
        return value;
    }

    @SuppressWarnings({"unused", "unchecked"})
    private static LookupResult getPropAndThis(
            Object obj, String name, Context cx, VarScope scope) {
        if (obj instanceof VarScope) {
            return ScriptRuntime.getPropAndThis(obj, name, cx, scope);
        }
        SlotMapOwner<Scriptable> owner = (SlotMapOwner<Scriptable>) obj;
        Object value = owner.get(name, (Scriptable) obj);
        Scriptable proto = ((Scriptable) obj).getPrototype();
        while (proto != null && value == Scriptable.NOT_FOUND) {
            value = proto.get(name, (Scriptable) obj);
            proto = proto.getPrototype();
        }
        if (value == Scriptable.NOT_FOUND) {
            // Complete miss: fall back to the full lookup, which also performs the
            // legacy __noSuchMethod__ search.
            return ScriptRuntime.getPropAndThis(obj, name, cx, scope);
        }
        return LookupResult.newLookupResult(value, (Scriptable) obj, name);
    }

    @SuppressWarnings({"unused", "unchecked"})
    private static LookupResult getPropAndThisOptional(
            Object obj, String name, Context cx, VarScope scope) {
        if (obj instanceof VarScope) {
            return ScriptRuntime.getPropAndThisOptional(obj, name, cx, scope);
        }
        SlotMapOwner<Scriptable> owner = (SlotMapOwner<Scriptable>) obj;
        Object value = owner.get(name, (Scriptable) obj);
        Scriptable proto = ((Scriptable) obj).getPrototype();
        while (proto != null && value == Scriptable.NOT_FOUND) {
            value = proto.get(name, (Scriptable) obj);
            proto = proto.getPrototype();
        }
        if (value == Scriptable.NOT_FOUND) {
            // Complete miss: fall back to the full lookup, which also performs the
            // legacy __noSuchMethod__ search.
            return ScriptRuntime.getPropAndThisOptional(obj, name, cx, scope);
        }
        if (!(value instanceof Callable) && (value == null || Undefined.isUndefined(value))) {
            return null;
        }
        return LookupResult.newLookupResult(value, (Scriptable) obj, name);
    }
}
