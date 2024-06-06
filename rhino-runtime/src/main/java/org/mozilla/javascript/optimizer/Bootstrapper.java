/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.optimizer;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.mozilla.classfile.ByteCode;
import org.mozilla.classfile.ClassFileWriter;
import org.mozilla.javascript.ScriptRuntime;

public class Bootstrapper {

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
                    "org.mozilla.javascript.optimizer.Bootstrapper",
                    "bootstrap",
                    Bootstrapper.BOOTSTRAP_SIGNATURE);

    static final boolean DEBUG = false;

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
    @SuppressWarnings("unused")
    public static CallSite bootstrap(MethodHandles.Lookup lookup, String name, MethodType mType)
            throws NoSuchMethodException, IllegalAccessException {
        if (DEBUG) {
            System.out.println("Bootstrap: " + name);
        }
        if (name.startsWith("PROP:")) {
            String opName = name.substring(5);
            if (opName.startsWith("GET:")) {
                // Interning is important here because this is only called once per call site
                // and repeated calls are much faster when we do
                String propertyName = opName.substring(4).intern();
                MethodType tt = mType.insertParameterTypes(1, String.class);
                MethodHandle getMethod =
                        lookup.findStatic(ScriptRuntime.class, "getObjectProp", tt);
                MethodHandle mh = MethodHandles.insertArguments(getMethod, 1, propertyName);
                return new ConstantCallSite(mh);
            } else if (opName.startsWith("GETNOWARN:")) {
                String propertyName = opName.substring(10).intern();
                MethodType tt = mType.insertParameterTypes(1, String.class);
                MethodHandle getMethod =
                        lookup.findStatic(ScriptRuntime.class, "getObjectPropNoWarn", tt);
                MethodHandle mh = MethodHandles.insertArguments(getMethod, 1, propertyName);
                return new ConstantCallSite(mh);
            } else if (opName.startsWith("SET:")) {
                String propertyName = opName.substring(4).intern();
                MethodType tt = mType.insertParameterTypes(1, String.class);
                MethodHandle getMethod =
                        lookup.findStatic(ScriptRuntime.class, "setObjectProp", tt);
                MethodHandle mh = MethodHandles.insertArguments(getMethod, 1, propertyName);
                return new ConstantCallSite(mh);
            }
        } else if (name.startsWith("NAME:")) {
            String opName = name.substring(5);
            if (opName.startsWith("GET:")) {
                String propertyName = opName.substring(4).intern();
                MethodType tt = mType.insertParameterTypes(2, String.class);
                MethodHandle getMethod = lookup.findStatic(ScriptRuntime.class, "name", tt);
                MethodHandle mh = MethodHandles.insertArguments(getMethod, 2, propertyName);
                return new ConstantCallSite(mh);
            }
        }
        throw new NoSuchMethodException(name);
    }
}
