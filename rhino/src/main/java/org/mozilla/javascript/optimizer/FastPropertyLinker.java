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
import org.mozilla.javascript.Callable;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.SlotMap;
import org.mozilla.javascript.WellBehavedProperties;

/**
 * This linker works with ScriptableObject to use the fast property support, which allows access to
 * properties by index. We use it by getting the index, and then using that to short-circuit lookup
 * when we are looking at properties of the same object repeatedly.
 */
@SuppressWarnings("AndroidJdkLibsChecker")
class FastPropertyLinker implements TypeBasedGuardingDynamicLinker {
    @Override
    public boolean canLinkType(Class<?> type) {
        // We can only link here if we know that the object will behave well,
        // and not override "get" and "set" in ways that make the fast property
        // optimization break down. The WellBehavedProperties annotation is
        // therefore only used on classes when we know that is the case.
        return ScriptableObject.class.isAssignableFrom(type)
                && type.isAnnotationPresent(WellBehavedProperties.class);
        // TODO NativeWith?
    }

    @Override
    public GuardedInvocation getGuardedInvocation(LinkRequest req, LinkerServices svc)
            throws Exception {
        if (req.isCallSiteUnstable()) {
            return null;
        }

        ParsedOperation op = new ParsedOperation(req.getCallSiteDescriptor().getOperation());
        Object target = req.getReceiver();
        assert target instanceof ScriptableObject;

        if (op.isNamespace(StandardNamespace.PROPERTY)
                && op.isOperation(StandardOperation.GET, RhinoOperation.GETNOWARN)) {
            SlotMap.FastKey fk = ((ScriptableObject) target).lookupFast(op.getName());
            if (fk != null) {
                return getFastRead(req, op, fk, 3, "testFastProperty", "getFastProperty");
            }
        } else if (op.isNamespace(RhinoNamespace.NAME)
                && op.isOperation(StandardOperation.GET, RhinoOperation.GETWITHTHIS)) {
            ScriptableObject so = (ScriptableObject) target;
            SlotMap.FastKey fk = so.lookupFast(op.getName());
            if (fk != null) {
                if (op.isOperation(RhinoOperation.GETWITHTHIS)) {
                    return getFastRead(req, op, fk, 2, "testFastName", "getFastNameAndThis");
                }
                return getFastRead(req, op, fk, 2, "testFastName", "getFastName");
            }
        }

        return null;
    }

    private GuardedInvocation getFastRead(
            LinkRequest req,
            ParsedOperation op,
            SlotMap.FastKey fk,
            int targetArity,
            String testName,
            String getName)
            throws NoSuchMethodException, IllegalAccessException {
        if (DefaultLinker.DEBUG) {
            System.out.println(op + " fast lookup");
        }
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodType mType = req.getCallSiteDescriptor().getMethodType();

        MethodType guardType =
                mType.changeReturnType(Boolean.TYPE)
                        .insertParameterTypes(targetArity, SlotMap.FastKey.class);
        MethodHandle guard = lookup.findStatic(FastPropertyLinker.class, testName, guardType);
        guard = MethodHandles.insertArguments(guard, targetArity, fk);

        MethodType invokeType =
                mType.insertParameterTypes(targetArity, SlotMap.FastKey.class, String.class);
        MethodHandle mh = lookup.findStatic(FastPropertyLinker.class, getName, invokeType);
        mh = MethodHandles.insertArguments(mh, targetArity, fk, op.getName());

        return new GuardedInvocation(mh, guard);
    }

    @SuppressWarnings("unused")
    private static boolean testFastProperty(
            Object o, Context cx, Scriptable scope, SlotMap.FastKey k) {
        if (o instanceof ScriptableObject) {
            return ((ScriptableObject) o).validateFast(k);
        }
        return false;
    }

    @SuppressWarnings("unused")
    private static Object getFastProperty(
            Object o, Context cx, Scriptable scope, SlotMap.FastKey k, String name) {
        return ((ScriptableObject) o).getFast(k);
    }

    @SuppressWarnings("unused")
    private static boolean testFastName(Scriptable s, Context cx, SlotMap.FastKey k) {
        if (s instanceof ScriptableObject) {
            return ((ScriptableObject) s).validateFast(k);
        }
        return false;
    }

    @SuppressWarnings("unused")
    private static Object getFastName(Scriptable s, Context cx, SlotMap.FastKey k, String name) {
        return ((ScriptableObject) s).getFast(k);
    }

    @SuppressWarnings("unused")
    private static Callable getFastNameAndThis(
            Scriptable s, Context cx, SlotMap.FastKey k, String name) {
        Object ret = ((ScriptableObject) s).getFast(k);
        if (ret instanceof Callable) {
            ScriptRuntime.storeScriptable(cx, s);
            return (Callable) ret;
        }
        if (ret == Scriptable.NOT_FOUND) {
            throw ScriptRuntime.notFoundError(s, name);
        }
        throw ScriptRuntime.notFunctionError(s, name);
    }
}
