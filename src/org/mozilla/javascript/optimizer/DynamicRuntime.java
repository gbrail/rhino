/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.optimizer;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import jdk.dynalink.CallSiteDescriptor;
import jdk.dynalink.DynamicLinker;
import jdk.dynalink.DynamicLinkerFactory;
import jdk.dynalink.NamedOperation;
import jdk.dynalink.Namespace;
import jdk.dynalink.NamespaceOperation;
import jdk.dynalink.Operation;
import jdk.dynalink.StandardNamespace;
import jdk.dynalink.StandardOperation;
import jdk.dynalink.linker.GuardedInvocation;
import jdk.dynalink.linker.GuardingDynamicLinker;
import jdk.dynalink.linker.LinkRequest;
import jdk.dynalink.linker.LinkerServices;
import jdk.dynalink.support.ChainedCallSite;
import org.mozilla.classfile.ByteCode;
import org.mozilla.classfile.ClassFileWriter;
import org.mozilla.javascript.ScriptRuntime;

public class DynamicRuntime {

    public static final String BOOTSTRAP_SIGNATURE =
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;";

    public static final String GET_PROP_SIGNATURE =
            "(Ljava/lang/Object;Lorg/mozilla/javascript/Context;Lorg/mozilla/javascript/Scriptable;)Ljava/lang/Object;";
    public static final String SET_PROP_SIGNATURE =
            "(Ljava/lang/Object;Ljava/lang/Object;Lorg/mozilla/javascript/Context;Lorg/mozilla/javascript/Scriptable;)Ljava/lang/Object;";
    public static final String GET_NAME_SIGNATURE =
            "(Lorg/mozilla/javascript/Context;Lorg/mozilla/javascript/Scriptable;)Ljava/lang/Object;";

    public static final ClassFileWriter.MHandle BOOTSTRAP_HANDLE =
            new ClassFileWriter.MHandle(
                    ByteCode.MH_INVOKESTATIC,
                    "org.mozilla.javascript.optimizer.DynamicRuntime",
                    "bootstrap",
                    DynamicRuntime.BOOTSTRAP_SIGNATURE);

    private static final DynamicLinker LINKER;

    static final boolean DEBUG = false;

    static {
        // Construct our linker.
        DynamicLinkerFactory linkerFactory = new DynamicLinkerFactory();
        linkerFactory.setPrioritizedLinkers(new PropertyLinker(), new FallbackLinker());
        LINKER = linkerFactory.createLinker();
    }

    @SuppressWarnings("unused")
    public static CallSite bootstrap(MethodHandles.Lookup lookup, String name, MethodType mType)
            throws NoSuchMethodException, IllegalAccessException {
        // Link using a ChainedCallSite. This will cache a number of possibilities for each
        // call site, and adjust them if the sitation changes.
        return LINKER.link(
                new ChainedCallSite(new CallSiteDescriptor(lookup, parseOperation(name), mType)));
    }

    /**
     * This method parses the operation names that we use in the INDY instructions to objects that
     * our linkers will use to efficiently link the call sites. Supported names are:
     *
     * <ul>
     *   <li>PROP:GET:name Get an object property called "name"
     *   <li>PROP:GETNOWARN:name Get without a missing property warning
     *   <li>PROP:SET:name Set it
     *   <li>NAME:GET:name Get value "name" from the supplied scope
     * </ul>
     */
    private static Operation parseOperation(String name) throws NoSuchMethodException {
        if (name.startsWith("PROP:")) {
            String opName = name.substring(5);
            if (opName.startsWith("GET:")) {
                // Interning is important here because this is only called once per call site
                // and repeated calls are much faster when we do
                String propertyName = opName.substring(4).intern();
                return StandardOperation.GET
                        .withNamespace(StandardNamespace.PROPERTY)
                        .named(propertyName);
            } else if (opName.startsWith("GETNOWARN:")) {
                String propertyName = opName.substring(10).intern();
                return RhinoOperation.GETNOWARN
                        .withNamespace(StandardNamespace.PROPERTY)
                        .named(propertyName);
            } else if (opName.startsWith("SET:")) {
                String propertyName = opName.substring(4).intern();
                return StandardOperation.SET
                        .withNamespace(StandardNamespace.PROPERTY)
                        .named(propertyName);
            }
        } else if (name.startsWith("NAME:")) {
            String opName = name.substring(5);
            if (opName.startsWith("GET:")) {
                String propertyName = opName.substring(4).intern();
                return StandardOperation.GET.withNamespace(RhinoNamespace.NAME).named(propertyName);
            }
        }
        throw new NoSuchMethodException(name);
    }

    /**
     * This linker will resolve all call sites to the methods in ScriptableRuntime that the bytecode
     * has always used for various operations. It does no special optimizations, and should always
     * be last in the chain, because once it's called, no more optimizations will happen.
     */
    static class FallbackLinker implements GuardingDynamicLinker {
        @Override
        public GuardedInvocation getGuardedInvocation(LinkRequest req, LinkerServices svcs)
                throws NoSuchMethodException, IllegalAccessException {
            Operation op = req.getCallSiteDescriptor().getOperation();
            if (DEBUG) {
                System.out.println(op + " -> fallback link");
            }

            String propertyName = getPropertyName(op);
            op = NamedOperation.getBaseOperation(op);
            Namespace namespace = getNamespace(op);
            op = NamespaceOperation.getBaseOperation(op);

            MethodType mType = req.getCallSiteDescriptor().getMethodType();
            MethodHandles.Lookup lookup = req.getCallSiteDescriptor().getLookup();

            if (namespace == StandardNamespace.PROPERTY) {
                if (op == StandardOperation.GET) {
                    MethodType tt = mType.insertParameterTypes(1, String.class);
                    MethodHandle getMethod =
                            lookup.findStatic(ScriptRuntime.class, "getObjectProp", tt);
                    MethodHandle mh = MethodHandles.insertArguments(getMethod, 1, propertyName);
                    return new GuardedInvocation(mh);

                } else if (op == RhinoOperation.GETNOWARN) {
                    MethodType tt = mType.insertParameterTypes(1, String.class);
                    MethodHandle getMethod =
                            lookup.findStatic(ScriptRuntime.class, "getObjectPropNoWarn", tt);
                    MethodHandle mh = MethodHandles.insertArguments(getMethod, 1, propertyName);
                    return new GuardedInvocation(mh);

                } else if (op == StandardOperation.SET) {
                    MethodType tt = mType.insertParameterTypes(1, String.class);
                    MethodHandle getMethod =
                            lookup.findStatic(ScriptRuntime.class, "setObjectProp", tt);
                    MethodHandle mh = MethodHandles.insertArguments(getMethod, 1, propertyName);
                    return new GuardedInvocation(mh);
                }

            } else if (namespace == RhinoNamespace.NAME) {
                if (op == StandardOperation.GET) {
                    MethodType tt = mType.insertParameterTypes(2, String.class);
                    MethodHandle getMethod = lookup.findStatic(ScriptRuntime.class, "name", tt);
                    MethodHandle mh = MethodHandles.insertArguments(getMethod, 2, propertyName);
                    return new GuardedInvocation(mh);
                }
            }

            throw new UnsupportedOperationException("Invalid operation " + op);
        }
    }

    static Namespace getNamespace(Operation op) {
        Namespace[] namespaces = NamespaceOperation.getNamespaces(op);
        if (namespaces.length != 1) {
            throw new UnsupportedOperationException("Operation must have one namespace");
        }
        return namespaces[0];
    }

    static String getPropertyName(Operation op) {
        Object name = NamedOperation.getName(op);
        if (name == null) {
            return null;
        }
        if (!(name instanceof String)) {
            throw new UnsupportedOperationException("Operation must have a string name");
        }
        return (String) name;
    }
}
