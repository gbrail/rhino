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
    public static final String INCRDECR_PROP_SIGNATURE =
            "(Ljava/lang/Object;Lorg/mozilla/javascript/Context;Lorg/mozilla/javascript/Scriptable;I)Ljava/lang/Object;";

    public static final String GET_NAME_SIGNATURE =
            "(Lorg/mozilla/javascript/Context;Lorg/mozilla/javascript/Scriptable;)Ljava/lang/Object;";
    public static final String SET_NAME_SIGNATURE =
            "(Lorg/mozilla/javascript/Scriptable;Ljava/lang/Object;Lorg/mozilla/javascript/Context;Lorg/mozilla/javascript/Scriptable;)Ljava/lang/Object;";
    public static final String INCRDECR_NAME_SIGNATURE =
            "(Lorg/mozilla/javascript/Scriptable;Lorg/mozilla/javascript/Context;I)Ljava/lang/Object;";

    public static final String BIND_SIGNATURE =
            "(Lorg/mozilla/javascript/Context;Lorg/mozilla/javascript/Scriptable;)Lorg/mozilla/javascript/Scriptable;";

    public static final String FUNCTHIS_PROP_SIGNATURE =
            "(Ljava/lang/Object;Lorg/mozilla/javascript/Context;Lorg/mozilla/javascript/Scriptable;)Lorg/mozilla/javascript/Callable;";
    public static final String FUNCTHIS_NAME_SIGNATURE =
            "(Lorg/mozilla/javascript/Context;Lorg/mozilla/javascript/Scriptable;)Lorg/mozilla/javascript/Callable;";
    public static final String FUNCTHIS_ELEM_SIGNATURE =
            "(Ljava/lang/Object;Ljava/lang/Object;Lorg/mozilla/javascript/Context;Lorg/mozilla/javascript/Scriptable;)Lorg/mozilla/javascript/Callable;";
    public static final String FUNCTHIS_VALUE_SIGNATURE =
            "(Ljava/lang/Object;Lorg/mozilla/javascript/Context;)Lorg/mozilla/javascript/Callable;";

    public static final String OBJECT_ELEM_GET_SIGNATURE =
            "(Ljava/lang/Object;Ljava/lang/Object;Lorg/mozilla/javascript/Context;Lorg/mozilla/javascript/Scriptable;)Ljava/lang/Object;";
    public static final String OBJECT_ELEM_SET_SIGNATURE =
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Lorg/mozilla/javascript/Context;Lorg/mozilla/javascript/Scriptable;)Ljava/lang/Object;";

    public static final String OBJECT_INDEX_GET_SIGNATURE =
            "(Ljava/lang/Object;DLorg/mozilla/javascript/Context;Lorg/mozilla/javascript/Scriptable;)Ljava/lang/Object;";
    public static final String OBJECT_INDEX_SET_SIGNATURE =
            "(Ljava/lang/Object;DLjava/lang/Object;Lorg/mozilla/javascript/Context;Lorg/mozilla/javascript/Scriptable;)Ljava/lang/Object;";

    public static final String CALL_NAME_0_SIGNATURE =
            "(Lorg/mozilla/javascript/Context;Lorg/mozilla/javascript/Scriptable;)Ljava/lang/Object;";
    public static final String CALL_PROP_0_SIGNATURE =
            "(Ljava/lang/Object;Lorg/mozilla/javascript/Context;Lorg/mozilla/javascript/Scriptable;)Ljava/lang/Object;";
    public static final String CALL_0_SIGNATURE =
            "(Lorg/mozilla/javascript/Callable;Lorg/mozilla/javascript/Scriptable;Lorg/mozilla/javascript/Context;Lorg/mozilla/javascript/Scriptable;)Ljava/lang/Object;";
    public static final String CALL_1_SIGNATURE =
            "(Lorg/mozilla/javascript/Callable;Lorg/mozilla/javascript/Scriptable;Ljava/lang/Object;Lorg/mozilla/javascript/Context;Lorg/mozilla/javascript/Scriptable;)Ljava/lang/Object;";
    public static final String CALL_2_SIGNATURE =
            "(Lorg/mozilla/javascript/Callable;Lorg/mozilla/javascript/Scriptable;Ljava/lang/Object;Ljava/lang/Object;Lorg/mozilla/javascript/Context;Lorg/mozilla/javascript/Scriptable;)Ljava/lang/Object;";
    public static final String CALL_N_SIGNATURE =
            "(Lorg/mozilla/javascript/Callable;Lorg/mozilla/javascript/Scriptable;[Ljava/lang/Object;Lorg/mozilla/javascript/Context;Lorg/mozilla/javascript/Scriptable;)Ljava/lang/Object;";
    public static final String CALL_NAME_SIGNATURE =
            "([Ljava/lang/Object;Lorg/mozilla/javascript/Context;Lorg/mozilla/javascript/Scriptable;)Ljava/lang/Object;";

    public static final String MATH_SIGNATURE =
            "(Ljava/lang/Number;Ljava/lang/Number;)Ljava/lang/Number;";
    public static final String MATH_1_SIGNATURE = "(Ljava/lang/Number;)Ljava/lang/Number;";
    public static final String ADD_SIGNATURE =
            "(Ljava/lang/Object;Ljava/lang/Object;Lorg/mozilla/javascript/Context;)Ljava/lang/Object;";
    public static final String ADD_LEFT_SIGNATURE =
            "(DLjava/lang/Object;Lorg/mozilla/javascript/Context;)Ljava/lang/Object;";
    public static final String ADD_RIGHT_SIGNATURE =
            "(Ljava/lang/Object;DLorg/mozilla/javascript/Context;)Ljava/lang/Object;";
    public static final String COMPARE_SIGNATURE = "(Ljava/lang/Object;Ljava/lang/Object;I)Z";
    public static final String EQ_SIGNATURE = "(Ljava/lang/Object;Ljava/lang/Object;)Z";

    public static final String TYPEOF_SIGNATURE = "(Ljava/lang/Object;)Ljava/lang/String;";
    public static final String TYPEOF_NAME_SIGNATURE =
            "(Lorg/mozilla/javascript/Scriptable;)Ljava/lang/String;";
    public static final String TOBOOLEAN_SIGNATURE = "(Ljava/lang/Object;)Z";
    public static final String TONUMBER_SIGNATURE = "(Ljava/lang/Object;)D";
    public static final String TONUMERIC_SIGNATURE = "(Ljava/lang/Object;)Ljava/lang/Number;";
    public static final String TOINT32_SIGNATURE = "(Ljava/lang/Object;)I";
    public static final String TOUINT32_SIGNATURE = "(Ljava/lang/Object;)J";

    public static final ClassFileWriter.MHandle BOOTSTRAP_HANDLE =
            new ClassFileWriter.MHandle(
                    ByteCode.MH_INVOKESTATIC,
                    "org.mozilla.javascript.optimizer.Bootstrapper",
                    "bootstrap",
                    Bootstrapper.BOOTSTRAP_SIGNATURE);

    static final boolean DEBUG = false;

    /**
     * This method parses the operation names that we use in the INDY instructions to objects that
     * our linkers will use to efficiently link the call sites. Supported names include:
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
                MethodType tt = mType.insertParameterTypes(0, PropertyCallSite.class);
                PropertyCallSite site = new PropertyCallSite(propertyName, mType);
                MethodHandle m = lookup.findStatic(DynamicOperations.class, "getObjectProp", tt);
                MethodHandle mh = MethodHandles.insertArguments(m, 0, site);
                site.setTarget(mh);
                return site;
            } else if (opName.startsWith("GETNOWARN:")) {
                String propertyName = opName.substring(10).intern();
                MethodType tt = mType.insertParameterTypes(1, String.class);
                MethodHandle m = lookup.findStatic(ScriptRuntime.class, "getObjectPropNoWarn", tt);
                MethodHandle mh = MethodHandles.insertArguments(m, 1, propertyName);
                return new ConstantCallSite(mh);
            } else if (opName.startsWith("SET:")) {
                String propertyName = opName.substring(4).intern();
                MethodType tt = mType.insertParameterTypes(1, String.class);
                MethodHandle m = lookup.findStatic(ScriptRuntime.class, "setObjectProp", tt);
                MethodHandle mh = MethodHandles.insertArguments(m, 1, propertyName);
                return new ConstantCallSite(mh);
            } else if (opName.startsWith("INCRDECR:")) {
                String propertyName = opName.substring(9).intern();
                MethodType tt = mType.insertParameterTypes(1, String.class);
                MethodHandle m = lookup.findStatic(ScriptRuntime.class, "propIncrDecr", tt);
                MethodHandle mh = MethodHandles.insertArguments(m, 1, propertyName);
                return new ConstantCallSite(mh);
            }
        } else if (name.startsWith("NAME:")) {
            String opName = name.substring(5);
            if (opName.startsWith("GET:")) {
                String propertyName = opName.substring(4).intern();
                MethodType tt = mType.insertParameterTypes(2, String.class);
                MethodHandle m = lookup.findStatic(ScriptRuntime.class, "name", tt);
                MethodHandle mh = MethodHandles.insertArguments(m, 2, propertyName);
                return new ConstantCallSite(mh);
            } else if (opName.startsWith("SET:")) {
                String propertyName = opName.substring(4).intern();
                MethodType tt = mType.insertParameterTypes(4, String.class);
                MethodHandle m = lookup.findStatic(ScriptRuntime.class, "setName", tt);
                MethodHandle mh = MethodHandles.insertArguments(m, 4, propertyName);
                return new ConstantCallSite(mh);
            } else if (opName.startsWith("SETSTRICT:")) {
                String propertyName = opName.substring(10).intern();
                MethodType tt = mType.insertParameterTypes(4, String.class);
                MethodHandle m = lookup.findStatic(ScriptRuntime.class, "strictSetName", tt);
                MethodHandle mh = MethodHandles.insertArguments(m, 4, propertyName);
                return new ConstantCallSite(mh);
            } else if (opName.startsWith("INCRDECR:")) {
                String propertyName = opName.substring(9).intern();
                MethodType tt = mType.insertParameterTypes(1, String.class);
                MethodHandle m = lookup.findStatic(ScriptRuntime.class, "nameIncrDecr", tt);
                MethodHandle mh = MethodHandles.insertArguments(m, 1, propertyName);
                return new ConstantCallSite(mh);
            }
        } else if (name.startsWith("BIND:")) {
            String propertyName = name.substring(5).intern();
            MethodType tt = mType.insertParameterTypes(2, String.class);
            MethodHandle m = lookup.findStatic(ScriptRuntime.class, "bind", tt);
            MethodHandle mh = MethodHandles.insertArguments(m, 2, propertyName);
            return new ConstantCallSite(mh);
        } else if (name.startsWith("GETFUNCTHIS:")) {
            String opName = name.substring(12);
            if (opName.startsWith("PROP:")) {
                String propertyName = opName.substring(5).intern();
                MethodType tt = mType.insertParameterTypes(1, String.class);
                MethodHandle m =
                        lookup.findStatic(ScriptRuntime.class, "getPropFunctionAndThis", tt);
                MethodHandle mh = MethodHandles.insertArguments(m, 1, propertyName);
                return new ConstantCallSite(mh);
            } else if (opName.startsWith("NAME:")) {
                String propertyName = opName.substring(5).intern();
                MethodType tt = mType.insertParameterTypes(0, String.class);
                MethodHandle m =
                        lookup.findStatic(ScriptRuntime.class, "getNameFunctionAndThis", tt);
                MethodHandle mh = MethodHandles.insertArguments(m, 0, propertyName);
                return new ConstantCallSite(mh);
            } else if (opName.equals("ELEM")) {
                MethodHandle mh =
                        lookup.findStatic(ScriptRuntime.class, "getElemFunctionAndThis", mType);
                return new ConstantCallSite(mh);
            } else if (opName.equals("VALUE")) {
                MethodHandle mh =
                        lookup.findStatic(ScriptRuntime.class, "getValueFunctionAndThis", mType);
                return new ConstantCallSite(mh);
            }
        } else if (name.startsWith("OBJ:")) {
            String oName = name.substring(4);
            if (oName.startsWith("ELEM:")) {
                String opName = oName.substring(5);
                if (opName.equals("GET")) {
                    MethodHandle mh =
                            lookup.findStatic(ScriptRuntime.class, "getObjectElem", mType);
                    return new ConstantCallSite(mh);
                } else if (opName.equals("SET")) {
                    MethodHandle mh =
                            lookup.findStatic(ScriptRuntime.class, "setObjectElem", mType);
                    return new ConstantCallSite(mh);
                }
            } else if (oName.startsWith("INDEX:")) {
                String opName = oName.substring(6);
                if (opName.equals("GET")) {
                    MethodHandle mh =
                            lookup.findStatic(ScriptRuntime.class, "getObjectIndex", mType);
                    return new ConstantCallSite(mh);
                } else if (opName.equals("SET")) {
                    MethodHandle mh =
                            lookup.findStatic(ScriptRuntime.class, "setObjectIndex", mType);
                    return new ConstantCallSite(mh);
                }
            }
        } else if (name.startsWith("CALL:")) {
            String opName = name.substring(5);
            if (opName.startsWith("NAME0:")) {
                String propName = opName.substring(6).intern();
                MethodType tt = mType.insertParameterTypes(0, String.class);
                MethodHandle m = lookup.findStatic(OptRuntime.class, "callName0", tt);
                MethodHandle mh = MethodHandles.insertArguments(m, 0, propName);
                return new ConstantCallSite(mh);
            } else if (opName.startsWith("NAME:")) {
                String propName = opName.substring(5).intern();
                MethodType tt = mType.insertParameterTypes(1, String.class);
                MethodHandle m = lookup.findStatic(OptRuntime.class, "callName", tt);
                MethodHandle mh = MethodHandles.insertArguments(m, 1, propName);
                return new ConstantCallSite(mh);
            } else if (opName.startsWith("PROP0:")) {
                String propName = opName.substring(6).intern();
                MethodType tt = mType.insertParameterTypes(1, String.class);
                MethodHandle m = lookup.findStatic(OptRuntime.class, "callProp0", tt);
                MethodHandle mh = MethodHandles.insertArguments(m, 1, propName);
                return new ConstantCallSite(mh);
            } else if (opName.equals("ZERO")) {
                MethodHandle mh = lookup.findStatic(OptRuntime.class, "call0", mType);
                return new ConstantCallSite(mh);
            } else if (opName.equals("ONE")) {
                MethodHandle mh = lookup.findStatic(OptRuntime.class, "call1", mType);
                return new ConstantCallSite(mh);
            } else if (opName.equals("TWO")) {
                MethodHandle mh = lookup.findStatic(OptRuntime.class, "call2", mType);
                return new ConstantCallSite(mh);
            } else if (opName.equals("N")) {
                MethodHandle mh = lookup.findStatic(OptRuntime.class, "callN", mType);
                return new ConstantCallSite(mh);
            }
        } else if (name.startsWith("MATH:")) {
            String opName = name.substring(5);
            MethodHandle mh;
            switch (opName) {
                case "SUB":
                    mh = lookup.findStatic(ScriptRuntime.class, "subtract", mType);
                    return new ConstantCallSite(mh);
                case "MUL":
                    mh = lookup.findStatic(ScriptRuntime.class, "multiply", mType);
                    return new ConstantCallSite(mh);
                case "DIV":
                    mh = lookup.findStatic(ScriptRuntime.class, "divide", mType);
                    return new ConstantCallSite(mh);
                case "MOD":
                    mh = lookup.findStatic(ScriptRuntime.class, "remainder", mType);
                    return new ConstantCallSite(mh);
                case "EXP":
                    mh = lookup.findStatic(ScriptRuntime.class, "exponentiate", mType);
                    return new ConstantCallSite(mh);
                case "BITOR":
                    mh = lookup.findStatic(ScriptRuntime.class, "bitwiseOR", mType);
                    return new ConstantCallSite(mh);
                case "BITXOR":
                    mh = lookup.findStatic(ScriptRuntime.class, "bitwiseXOR", mType);
                    return new ConstantCallSite(mh);
                case "BITAND":
                    mh = lookup.findStatic(ScriptRuntime.class, "bitwiseAND", mType);
                    return new ConstantCallSite(mh);
                case "BITNOT":
                    mh = lookup.findStatic(ScriptRuntime.class, "bitwiseNOT", mType);
                    return new ConstantCallSite(mh);
                case "RSH":
                    mh = lookup.findStatic(ScriptRuntime.class, "signedRightShift", mType);
                    return new ConstantCallSite(mh);
                case "LSH":
                    mh = lookup.findStatic(ScriptRuntime.class, "leftShift", mType);
                    return new ConstantCallSite(mh);
                case "ADD":
                    mh = lookup.findStatic(ScriptRuntime.class, "add", mType);
                    return new ConstantCallSite(mh);
                case "ADDLEFT":
                    mh = lookup.findStatic(OptRuntime.class, "add", mType);
                    return new ConstantCallSite(mh);
                case "ADDRIGHT":
                    mh = lookup.findStatic(OptRuntime.class, "add", mType);
                    return new ConstantCallSite(mh);
                case "NEGATE":
                    mh = lookup.findStatic(ScriptRuntime.class, "negate", mType);
                    return new ConstantCallSite(mh);
                case "CMP":
                    mh = lookup.findStatic(ScriptRuntime.class, "compare", mType);
                    return new ConstantCallSite(mh);
                case "EQ":
                    mh = lookup.findStatic(ScriptRuntime.class, "eq", mType);
                    return new ConstantCallSite(mh);
                case "SHALLOWEQ":
                    mh = lookup.findStatic(ScriptRuntime.class, "shallowEq", mType);
                    return new ConstantCallSite(mh);
            }
        } else if (name.startsWith("CONVERT:")) {
            String opName = name.substring(8);
            MethodHandle mh;
            if (opName.startsWith("TYPEOFNAME:")) {
                String prop = opName.substring(11).intern();
                MethodType tt = mType.insertParameterTypes(1, String.class);
                MethodHandle m = lookup.findStatic(ScriptRuntime.class, "typeofName", tt);
                mh = MethodHandles.insertArguments(m, 1, prop);
                return new ConstantCallSite(mh);
            }
            switch (opName) {
                case "TYPEOF":
                    mh = lookup.findStatic(ScriptRuntime.class, "typeof", mType);
                    return new ConstantCallSite(mh);
                case "TOBOOLEAN":
                    mh = lookup.findStatic(ScriptRuntime.class, "toBoolean", mType);
                    return new ConstantCallSite(mh);
                case "TONUMBER":
                    mh = lookup.findStatic(ScriptRuntime.class, "toNumber", mType);
                    return new ConstantCallSite(mh);
                case "TONUMERIC":
                    mh = lookup.findStatic(ScriptRuntime.class, "toNumeric", mType);
                    return new ConstantCallSite(mh);
                case "TOINT32":
                    mh = lookup.findStatic(ScriptRuntime.class, "toInt32", mType);
                    return new ConstantCallSite(mh);
                case "TOUINT32":
                    mh = lookup.findStatic(ScriptRuntime.class, "toUint32", mType);
                    return new ConstantCallSite(mh);
            }
        }
        throw new NoSuchMethodException(name);
    }
}
