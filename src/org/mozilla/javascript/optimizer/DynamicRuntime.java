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
    public enum RhinoOperation implements Operation {
        GETNOWARN,
    };

    public enum RhinoNamespace implements Namespace {
        NAME,
    };

    public static final String BOOTSTRAP_SIGNATURE =
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;";

    public static final String GET_PROP_SIGNATURE =
            "(Ljava/lang/Object;Lorg/mozilla/javascript/Context;Lorg/mozilla/javascript/Scriptable;)Ljava/lang/Object;";
    public static final String SET_PROP_SIGNATURE =
            "(Ljava/lang/Object;Ljava/lang/Object;Lorg/mozilla/javascript/Context;Lorg/mozilla/javascript/Scriptable;)Ljava/lang/Object;";
    public static final String GET_NAME_SIGNATURE =
            "(Lorg/mozilla/javascript/Context;Lorg/mozilla/javascript/Scriptable;)Ljava/lang/Object;";

    public static final ClassFileWriter.MHandle PROP_BOOTSTRAP_HANDLE =
            new ClassFileWriter.MHandle(
                    ByteCode.MH_INVOKESTATIC,
                    "org.mozilla.javascript.optimizer.DynamicRuntime",
                    "bootstrap",
                    DynamicRuntime.BOOTSTRAP_SIGNATURE);

    private static final DynamicLinker LINKER;

    static final boolean DEBUG = false;

    static {
        DynamicLinkerFactory linkerFactory = new DynamicLinkerFactory();
        linkerFactory.setPrioritizedLinkers(new PropertyLinker(), new FallbackLinker());
        LINKER = linkerFactory.createLinker();
    }

    @SuppressWarnings("unused")
    public static CallSite bootstrap(MethodHandles.Lookup lookup, String name, MethodType mType)
            throws NoSuchMethodException, IllegalAccessException {
        return LINKER.link(
                new ChainedCallSite(new CallSiteDescriptor(lookup, parseOperation(name), mType)));
    }

    private static Operation parseOperation(String name) throws NoSuchMethodException {
        if (name.startsWith("PROP:")) {
            String opName = name.substring(5);
            if (opName.startsWith("GET:")) {
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

    static class FallbackLinker implements GuardingDynamicLinker {
        @Override
        public GuardedInvocation getGuardedInvocation(LinkRequest req, LinkerServices svcs)
                throws NoSuchMethodException, IllegalAccessException {
            Operation rawOp = req.getCallSiteDescriptor().getOperation();
            if (DEBUG) {
                System.out.println(rawOp + " -> fallback link");
            }
            if (!(rawOp instanceof NamedOperation)) {
                throw new UnsupportedOperationException("Only named operations supported");
            }
            String propertyName = (String) ((NamedOperation) rawOp).getName();

            Operation rawNsOp = ((NamedOperation) rawOp).getBaseOperation();
            if (!(rawNsOp instanceof NamespaceOperation)) {
                throw new UnsupportedOperationException("Only namespace operations supported");
            }

            NamespaceOperation nsOp = (NamespaceOperation) rawNsOp;
            MethodType mType = req.getCallSiteDescriptor().getMethodType();
            MethodHandles.Lookup lookup = req.getCallSiteDescriptor().getLookup();
            Operation op = nsOp.getBaseOperation();

            if (nsOp.getNamespace(0) == StandardNamespace.PROPERTY) {
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

            } else if (nsOp.getNamespace(0) == RhinoNamespace.NAME) {
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
}
