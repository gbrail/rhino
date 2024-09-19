package org.mozilla.javascript.optimizer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
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
import jdk.dynalink.linker.support.Guards;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.ScriptableObject;

class ConstAwareLinker implements GuardingDynamicLinker {
    @Override
    public GuardedInvocation getGuardedInvocation(LinkRequest req, LinkerServices svc)
            throws Exception {
        if (req.isCallSiteUnstable()) {
            return null;
        }

        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Operation rootOp = req.getCallSiteDescriptor().getOperation();
        String name = DefaultLinker.getName(rootOp);
        Operation op = NamedOperation.getBaseOperation(rootOp);

        if (NamespaceOperation.contains(op, StandardOperation.GET, RhinoNamespace.NAME)) {
            return getConstantInvocation(rootOp, req, name, 1);
        } else if (NamespaceOperation.contains(
                        op, StandardOperation.GET, StandardNamespace.PROPERTY)
                || NamespaceOperation.contains(
                        op, RhinoOperation.GETNOWARN, StandardNamespace.PROPERTY)) {
            return getConstantInvocation(rootOp, req, name, 0);
        }

        return null;
    }

    /*
     * Return the value of the specified property, but only if it's found
     * and only if it's a constant.
     */
    private Optional<Object> getConstValue(Object root, String name) {
        // Look up the name on the specified object -- we can't handle things like prototype
        // or parent chains because that really complexifies the logic later.
        if (!(root instanceof ScriptableObject)) {
            return Optional.empty();
        }
        try {
            ScriptableObject obj = (ScriptableObject) root;
            if (obj.has(name, obj)) {
                int attributes = obj.getAttributes(name);
                if ((attributes & ScriptableObject.READONLY) != 0
                        && (attributes & ScriptableObject.PERMANENT) != 0
                        && (attributes & ScriptableObject.UNINITIALIZED_CONST) == 0) {
                    Object value = obj.get(name, obj);
                    // The value might be null. Unfortunately, because of the
                    // way that Optional works, it means that we can't
                    // constify a "null" result.
                    return Optional.ofNullable(value);
                }
            }
        } catch (RhinoException re) {
            // Some implementations will fail on this operation with
            // an exception, so treat that as "not found".
        }
        return Optional.empty();
    }

    /*
     * Return an invocation to return a property value as a const. It must be
     * supplied with the name of the property to look up and what position in the
     * arguments list contains the actual object to look them up on.
     */
    private GuardedInvocation getConstantInvocation(
            Operation rootOp, LinkRequest req, String name, int targetPos) {
        Object rawObj = req.getArguments()[targetPos];
        Optional<Object> constVal = getConstValue(rawObj, name);
        if (constVal.isPresent()) {
            if (DefaultLinker.DEBUG) {
                System.out.println(rootOp + ": constant");
            }
            // We found the value and it's a constant.
            // Set up a guard that will test whether it's being invoked with the original target
            // object
            MethodHandle guard =
                    Guards.asType(
                            Guards.getIdentityGuard(rawObj),
                            req.getCallSiteDescriptor().getMethodType());
            // Replace the invocation of the lookup with code that just returns the constant value.
            MethodHandle mh =
                    MethodHandles.dropArguments(
                            MethodHandles.constant(Object.class, constVal.get()),
                            0,
                            req.getCallSiteDescriptor().getMethodType().parameterList());

            return new GuardedInvocation(mh, guard);
        }

        return null;
    }
}
