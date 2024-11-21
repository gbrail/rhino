package org.mozilla.javascript.optimizer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import jdk.dynalink.StandardNamespace;
import jdk.dynalink.StandardOperation;
import jdk.dynalink.linker.GuardedInvocation;
import jdk.dynalink.linker.GuardingDynamicLinker;
import jdk.dynalink.linker.LinkRequest;
import jdk.dynalink.linker.LinkerServices;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

@SuppressWarnings("AndroidJdkLibsChecker")
class FastPropertyLinker implements GuardingDynamicLinker {
    @Override
    public GuardedInvocation getGuardedInvocation(LinkRequest req, LinkerServices svc)
            throws NoSuchMethodException, IllegalAccessException {
        if (req.isCallSiteUnstable()) {
            return null;
        }

        // MethodType mType = req.getCallSiteDescriptor().getMethodType();
        ParsedOperation op = new ParsedOperation(req.getCallSiteDescriptor().getOperation());
        Object target = req.getReceiver();
        if (!(target instanceof ScriptableObject)) {
            return null;
        }
        ScriptableObject obj = (ScriptableObject) target;
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        if (op.isNamespace(StandardNamespace.PROPERTY)
                && op.isOperation(StandardOperation.GET, RhinoOperation.GETNOWARN)) {
            ScriptableObject.FastKey key = obj.getFastKey(op.getName());
            if (key == null) {
                return null;
            }
            MethodType guardType =
                    MethodType.methodType(
                            Boolean.TYPE,
                            Object.class,
                            Context.class,
                            Scriptable.class,
                            ScriptableObject.FastKey.class);
            MethodHandle guard =
                    lookup.findStatic(FastPropertyLinker.class, "testPropGet", guardType);
            guard = MethodHandles.insertArguments(guard, 3, key);
            MethodType mType =
                    MethodType.methodType(
                            Object.class,
                            Object.class,
                            Context.class,
                            Scriptable.class,
                            ScriptableObject.FastKey.class);
            MethodHandle mh = lookup.findStatic(FastPropertyLinker.class, "propGet", mType);
            mh = MethodHandles.insertArguments(mh, 3, key);
            if (DefaultLinker.DEBUG) {
                System.out.println(op + ": get fast property");
            }
            return new GuardedInvocation(mh, guard);
        }

        return null;
    }

    @SuppressWarnings("unused")
    private static boolean testPropGet(
            Object target, Context cx, Scriptable scope, ScriptableObject.FastKey key) {
        if (target instanceof ScriptableObject) {
            return ((ScriptableObject) target).testFastKey(key);
        }
        return false;
    }

    @SuppressWarnings("unused")
    private static Object propGet(
            Object target, Context cx, Scriptable scope, ScriptableObject.FastKey key) {
        ScriptableObject obj = (ScriptableObject) target;
        Object result = obj.getFast(key, obj);
        if (result == Scriptable.NOT_FOUND) {
            return Undefined.instance;
        }
        return result;
    }
}
