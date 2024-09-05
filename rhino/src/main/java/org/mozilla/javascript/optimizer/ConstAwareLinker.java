package org.mozilla.javascript.optimizer;

import jdk.dynalink.Operation;
import jdk.dynalink.NamedOperation;
import jdk.dynalink.NamespaceOperation;
import jdk.dynalink.StandardOperation;
import jdk.dynalink.StandardNamespace;
import jdk.dynalink.linker.GuardedInvocation;
import jdk.dynalink.linker.GuardingDynamicLinker;
import jdk.dynalink.linker.LinkRequest;
import jdk.dynalink.linker.LinkerServices;
import jdk.dynalink.linker.support.Guards;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Context;
import java.util.Optional;

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
            return getNameGetInvocation(lookup, rootOp, req, name);
        } else if (NamespaceOperation.contains(op, RhinoOperation.GETWITHTHIS, StandardNamespace.PROPERTY)) {
            return getPropAndThisInvocation(lookup, rootOp, req, name);
        }
       
        return null;
    }

    /*
     * Return the value of the specified property, but only if it's a constant.
     */
    private Optional<Object> getConstValue(Object root, String name) {
        // Search the prototype chain for the name. If it's not there, we might
        // be doing things with parent scopes or other things, so just fall
        // back.
        if (!(root instanceof ScriptableObject)) {
            return Optional.empty();
        }
        ScriptableObject obj = (ScriptableObject) root;
        Scriptable start = obj;
        Object value;
        do {
            value = obj.get(name, start);
            if (value != Scriptable.NOT_FOUND) break;
            Scriptable proto = obj.getPrototype();
            if (!(proto instanceof ScriptableObject)) {
                return Optional.empty();
            }
            obj = (ScriptableObject) proto;
        } while (obj != null);

        if (value != Scriptable.NOT_FOUND) {
            int attributes = obj.getAttributes(name);
            if ((attributes & ScriptableObject.READONLY) != 0) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    private GuardedInvocation getNameGetInvocation(MethodHandles.Lookup lookup,
        Operation rootOp, LinkRequest req, String name) {
        Object rawObj = req.getArguments()[1];
        Optional<Object> constVal = getConstValue(rawObj, name);
        if (constVal.isPresent()) {
            if (DefaultLinker.DEBUG) {
                System.out.println(rootOp + ": constant");
            }
            // We found the value and it's a constant -- let's
            // wire it up so that we can get it fast
            MethodHandle guard =
                Guards.asType(Guards.getIdentityGuard(rawObj),
                    req.getCallSiteDescriptor().getMethodType());
            MethodHandle mh = 
                MethodHandles.dropArguments(MethodHandles.constant(Object.class, constVal.get()),
                0, Context.class, Scriptable.class);
                
            return new GuardedInvocation(mh, guard);
        }

        return null;
    }

    private GuardedInvocation getPropAndThisInvocation(MethodHandles.Lookup lookup,
        Operation rootOp, LinkRequest req, String name) {
        Object rawObj = req.getArguments()[0];
        Optional<Object> constVal = getConstValue(rawObj, name);
        if (constVal.isPresent()) {
            if (DefaultLinker.DEBUG) {
                System.out.println(rootOp + ": constant");
            }
            // We found the value and it's a constant -- let's
            // wire it up so that we can get it fast
            MethodHandle guard =
                Guards.asType(Guards.getIdentityGuard(rawObj),
                    req.getCallSiteDescriptor().getMethodType());
            MethodHandle mh = 
                MethodHandles.dropArguments(MethodHandles.constant(Object.class, constVal.get()),
                0, Object.class, Context.class, Scriptable.class);
                
            return new GuardedInvocation(mh, guard);
        }

        return null;
    }
}
