/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.optimizer;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.regex.Pattern;
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

    private static final Pattern SEPARATOR = Pattern.compile(":");

    static final boolean DEBUG = false;

    /**
     * This method parses the operation names that we use in the INDY instructions to objects that
     * our linkers will use to efficiently link the call sites.
     */
    @SuppressWarnings("unused")
    public static CallSite bootstrap(MethodHandles.Lookup lookup, String name, MethodType mType)
            throws NoSuchMethodException, IllegalAccessException {
        if (DEBUG) {
            System.out.println("Bootstrap: " + name);
        }

        String[] tokens = SEPARATOR.split(name);
        if (tokens.length < 2) {
            // All operation names have at least two tokens
            throw new NoSuchMethodException(name);
        }
        switch (tokens[0]) {
            case "PROP":
                return bootstrapProperties(lookup, mType, name, tokens);
            case "NAME":
                return bootstrapName(lookup, mType, name, tokens);
            case "BIND":
                return bootstrapBind(lookup, mType, name, tokens);
            case "GETFUNCTHIS":
                return bootstrapFuncThis(lookup, mType, name, tokens);
            case "OBJ":
                return bootstrapObject(lookup, mType, name, tokens);
            case "CALL":
                return bootstrapCall(lookup, mType, name, tokens);
            case "MATH":
                return bootstrapMath(lookup, mType, name, tokens);
            case "CONVERT":
                return bootstrapConvert(lookup, mType, name, tokens);
            default:
                throw new NoSuchMethodException(name);
        }
    }

    private static CallSite bootstrapProperties(
            MethodHandles.Lookup lookup, MethodType mType, String name, String[] tokens)
            throws NoSuchMethodException, IllegalAccessException {
        String prop = getPropertyName(name, tokens, 2);
        MethodType tt;
        MethodHandle m;
        MethodHandle mh;

        switch (tokens[1]) {
            case "GET":
                tt = mType.insertParameterTypes(1, String.class);
                m = lookup.findStatic(ScriptRuntime.class, "getObjectProp", tt);
                mh = MethodHandles.insertArguments(m, 1, prop);
                return new ConstantCallSite(mh);
            case "GETNOWARN":
                tt = mType.insertParameterTypes(1, String.class);
                m = lookup.findStatic(ScriptRuntime.class, "getObjectPropNoWarn", tt);
                mh = MethodHandles.insertArguments(m, 1, prop);
                return new ConstantCallSite(mh);
            case "SET":
                tt = mType.insertParameterTypes(1, String.class);
                m = lookup.findStatic(ScriptRuntime.class, "setObjectProp", tt);
                mh = MethodHandles.insertArguments(m, 1, prop);
                return new ConstantCallSite(mh);
            case "INCRDECR":
                tt = mType.insertParameterTypes(1, String.class);
                m = lookup.findStatic(ScriptRuntime.class, "propIncrDecr", tt);
                mh = MethodHandles.insertArguments(m, 1, prop);
                return new ConstantCallSite(mh);
            default:
                throw new NoSuchMethodException(name);
        }
    }

    private static CallSite bootstrapName(
            MethodHandles.Lookup lookup, MethodType mType, String name, String[] tokens)
            throws NoSuchMethodException, IllegalAccessException {
        String prop = getPropertyName(name, tokens, 2);
        MethodType tt;
        MethodHandle m;
        MethodHandle mh;

        switch (tokens[1]) {
            case "GET":
                tt = mType.insertParameterTypes(2, String.class);
                m = lookup.findStatic(ScriptRuntime.class, "name", tt);
                mh = MethodHandles.insertArguments(m, 2, prop);
                return new ConstantCallSite(mh);
            case "SET":
                tt = mType.insertParameterTypes(4, String.class);
                m = lookup.findStatic(ScriptRuntime.class, "setName", tt);
                mh = MethodHandles.insertArguments(m, 4, prop);
                return new ConstantCallSite(mh);
            case "SETSTRICT":
                tt = mType.insertParameterTypes(4, String.class);
                m = lookup.findStatic(ScriptRuntime.class, "strictSetName", tt);
                mh = MethodHandles.insertArguments(m, 4, prop);
                return new ConstantCallSite(mh);
            case "INCRDECR":
                tt = mType.insertParameterTypes(1, String.class);
                m = lookup.findStatic(ScriptRuntime.class, "nameIncrDecr", tt);
                mh = MethodHandles.insertArguments(m, 1, prop);
                return new ConstantCallSite(mh);
            default:
                throw new NoSuchMethodException(name);
        }
    }

    private static CallSite bootstrapBind(
            MethodHandles.Lookup lookup, MethodType mType, String name, String[] tokens)
            throws NoSuchMethodException, IllegalAccessException {
        String prop = getPropertyName(name, tokens, 1);
        MethodType tt = mType.insertParameterTypes(2, String.class);
        MethodHandle m = lookup.findStatic(ScriptRuntime.class, "bind", tt);
        MethodHandle mh = MethodHandles.insertArguments(m, 2, prop);
        return new ConstantCallSite(mh);
    }

    private static CallSite bootstrapFuncThis(
            MethodHandles.Lookup lookup, MethodType mType, String name, String[] tokens)
            throws NoSuchMethodException, IllegalAccessException {
        String prop;
        MethodType tt;
        MethodHandle m;
        MethodHandle mh;

        switch (tokens[1]) {
            case "PROP":
                prop = getPropertyName(name, tokens, 2);
                tt = mType.insertParameterTypes(1, String.class);
                m = lookup.findStatic(ScriptRuntime.class, "getPropFunctionAndThis", tt);
                mh = MethodHandles.insertArguments(m, 1, prop);
                return new ConstantCallSite(mh);
            case "NAME":
                prop = getPropertyName(name, tokens, 2);
                tt = mType.insertParameterTypes(0, String.class);
                m = lookup.findStatic(ScriptRuntime.class, "getNameFunctionAndThis", tt);
                mh = MethodHandles.insertArguments(m, 0, prop);
                return new ConstantCallSite(mh);
            case "ELEM":
                mh = lookup.findStatic(ScriptRuntime.class, "getElemFunctionAndThis", mType);
                return new ConstantCallSite(mh);
            case "VALUE":
                mh = lookup.findStatic(ScriptRuntime.class, "getValueFunctionAndThis", mType);
                return new ConstantCallSite(mh);
            default:
                throw new NoSuchMethodException(name);
        }
    }

    private static CallSite bootstrapObject(
            MethodHandles.Lookup lookup, MethodType mType, String name, String[] tokens)
            throws NoSuchMethodException, IllegalAccessException {
        MethodHandle mh;

        if (tokens.length < 3) {
            throw new NoSuchMethodException(name);
        }

        switch (tokens[1]) {
            case "ELEM":
                switch (tokens[2]) {
                    case "GET":
                        mh = lookup.findStatic(ScriptRuntime.class, "getObjectElem", mType);
                        return new ConstantCallSite(mh);
                    case "SET":
                        mh = lookup.findStatic(ScriptRuntime.class, "setObjectElem", mType);
                        return new ConstantCallSite(mh);
                    default:
                        throw new NoSuchMethodException(name);
                }
            case "INDEX":
                switch (tokens[2]) {
                    case "GET":
                        mh = lookup.findStatic(ScriptRuntime.class, "getObjectIndex", mType);
                        return new ConstantCallSite(mh);
                    case "SET":
                        mh = lookup.findStatic(ScriptRuntime.class, "setObjectIndex", mType);
                        return new ConstantCallSite(mh);
                    default:
                        throw new NoSuchMethodException(name);
                }
            default:
                throw new NoSuchMethodException(name);
        }
    }

    private static CallSite bootstrapCall(
            MethodHandles.Lookup lookup, MethodType mType, String name, String[] tokens)
            throws NoSuchMethodException, IllegalAccessException {
        String prop;
        MethodType tt;
        MethodHandle m;
        MethodHandle mh;

        switch (tokens[1]) {
            case "NAME0":
                prop = getPropertyName(name, tokens, 2);
                tt = mType.insertParameterTypes(0, String.class);
                m = lookup.findStatic(OptRuntime.class, "callName0", tt);
                mh = MethodHandles.insertArguments(m, 0, prop);
                return new ConstantCallSite(mh);
            case "NAME":
                prop = getPropertyName(name, tokens, 2);
                tt = mType.insertParameterTypes(1, String.class);
                m = lookup.findStatic(OptRuntime.class, "callName", tt);
                mh = MethodHandles.insertArguments(m, 1, prop);
                return new ConstantCallSite(mh);
            case "PROP0":
                prop = getPropertyName(name, tokens, 2);
                tt = mType.insertParameterTypes(1, String.class);
                m = lookup.findStatic(OptRuntime.class, "callProp0", tt);
                mh = MethodHandles.insertArguments(m, 1, prop);
                return new ConstantCallSite(mh);
            case "ZERO":
                mh = lookup.findStatic(OptRuntime.class, "call0", mType);
                return new ConstantCallSite(mh);
            case "ONE":
                mh = lookup.findStatic(OptRuntime.class, "call1", mType);
                return new ConstantCallSite(mh);
            case "TWO":
                mh = lookup.findStatic(OptRuntime.class, "call2", mType);
                return new ConstantCallSite(mh);
            case "N":
                mh = lookup.findStatic(OptRuntime.class, "callN", mType);
                return new ConstantCallSite(mh);
            default:
                throw new NoSuchMethodException(name);
        }
    }

    private static CallSite bootstrapMath(
            MethodHandles.Lookup lookup, MethodType mType, String name, String[] tokens)
            throws NoSuchMethodException, IllegalAccessException {
        MethodHandle mh;

        switch (tokens[1]) {
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
            default:
                throw new NoSuchMethodException(name);
        }
    }

    private static CallSite bootstrapConvert(
            MethodHandles.Lookup lookup, MethodType mType, String name, String[] tokens)
            throws NoSuchMethodException, IllegalAccessException {
        String prop;
        MethodType tt;
        MethodHandle m;
        MethodHandle mh;

        switch (tokens[1]) {
            case "TYPEOFNAME":
                prop = getPropertyName(name, tokens, 2);
                tt = mType.insertParameterTypes(1, String.class);
                m = lookup.findStatic(ScriptRuntime.class, "typeofName", tt);
                mh = MethodHandles.insertArguments(m, 1, prop);
                return new ConstantCallSite(mh);
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
            default:
                throw new NoSuchMethodException(name);
        }
    }

    private static String getPropertyName(String name, String[] tokens, int pos)
            throws NoSuchMethodException {
        if (tokens.length <= pos) {
            throw new NoSuchMethodException(name);
        }
        // Interning is important here because this is only called once per call site
        // and repeated calls are much faster when we do
        return tokens[pos].intern();
    }
}
