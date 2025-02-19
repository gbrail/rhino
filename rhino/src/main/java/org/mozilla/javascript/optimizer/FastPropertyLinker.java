package org.mozilla.javascript.optimizer;

import jdk.dynalink.StandardNamespace;
import jdk.dynalink.StandardOperation;
import jdk.dynalink.linker.GuardedInvocation;
import jdk.dynalink.linker.LinkRequest;
import jdk.dynalink.linker.LinkerServices;
import jdk.dynalink.linker.TypeBasedGuardingDynamicLinker;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * This linker works with ScriptableObject to use the fast property support, which
 * allows access to properties by index. We use it by getting the index, and then
 * using that to short-circuit lookup when we are looking at properties of
 * the same object repeatedly.
 */
@SuppressWarnings("AndroidJdkLibsChecker")
class FastPropertyLinker implements TypeBasedGuardingDynamicLinker {
    @Override
    public boolean canLinkType(Class<?> type) {
        return ScriptableObject.class.isAssignableFrom(type);
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

        if (op.isNamespace(StandardNamespace.PROPERTY)
                && op.isOperation(StandardOperation.GET, RhinoOperation.GETNOWARN)) {
            assert target instanceof ScriptableObject;
            int fastIndex = ((ScriptableObject) target).lookupFast(op.getName());
            if (fastIndex >= 0) {
                if (DefaultLinker.DEBUG) {
                    System.out.println(op + " fast property lookup");
                }
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                MethodType mType = req.getCallSiteDescriptor().getMethodType();

                MethodType guardType = mType.changeReturnType(Boolean.TYPE)
                        .insertParameterTypes(3, ScriptableObject.class, Integer.TYPE);
                MethodHandle guard = lookup.findStatic(FastPropertyLinker.class, "testFastProperty",
                        guardType);
                guard = MethodHandles.insertArguments(guard, 3, target, fastIndex);

                MethodType invokeType = mType.insertParameterTypes(3, Integer.TYPE);
                MethodHandle mh = lookup.findStatic(FastPropertyLinker.class, "getFastProperty",
                        invokeType);
                mh = MethodHandles.insertArguments(mh, 3, fastIndex);

                return new GuardedInvocation(mh, guard);
            }
        }

        return null;
    }

    /**
     * The target is still valid if it's still a valid index <em>and</em> it's
     * the same index.
     */
    @SuppressWarnings("unused")
    private static boolean testFastProperty(Object o, Context cx, Scriptable scope,
                                                ScriptableObject target, int fastIndex) {
        return o == target && target.validateFast(fastIndex);
    }

    @SuppressWarnings("unused")
    private static Object getFastProperty(Object o, Context cx, Scriptable scope, int fastIndex) {
        return ((ScriptableObject) o).getFast(fastIndex);
    }
}
