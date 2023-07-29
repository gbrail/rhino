package org.mozilla.javascript.optimizer;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
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

    public static final ClassFileWriter.MHandle GET_PROP_BOOTSTRAP =
            new ClassFileWriter.MHandle(
                    ByteCode.MH_INVOKESTATIC,
                    "org.mozilla.javascript.optimizer.DynamicRuntime",
                    "bootstrapGetProp",
                    DynamicRuntime.BOOTSTRAP_SIGNATURE);
    public static final ClassFileWriter.MHandle SET_PROP_BOOTSTRAP =
            new ClassFileWriter.MHandle(
                    ByteCode.MH_INVOKESTATIC,
                    "org.mozilla.javascript.optimizer.DynamicRuntime",
                    "bootstrapSetProp",
                    DynamicRuntime.BOOTSTRAP_SIGNATURE);

    @SuppressWarnings("unused")
    public static CallSite bootstrapGetProp(
            MethodHandles.Lookup lookup, String name, MethodType mType)
            throws NoSuchMethodException, IllegalAccessException {
        // Insert a String parameter to the list of parameters we're going to call
        MethodType getType = mType.insertParameterTypes(1, String.class);
        MethodHandle getProp = lookup.findStatic(ScriptRuntime.class, "getObjectProp", getType);
        // Always call with the "name" as the string parameter
        getProp = MethodHandles.insertArguments(getProp, 1, name);
        return new ConstantCallSite(getProp);
    }

    @SuppressWarnings("unused")
    public static CallSite bootstrapSetProp(
            MethodHandles.Lookup lookup, String name, MethodType mType)
            throws NoSuchMethodException, IllegalAccessException {
        // Insert a String parameter to the list of parameters we're going to call
        MethodType getType = mType.insertParameterTypes(1, String.class);
        MethodHandle getProp = lookup.findStatic(ScriptRuntime.class, "setObjectProp", getType);
        // Always call with the "name" as the string parameter
        getProp = MethodHandles.insertArguments(getProp, 1, name);
        return new ConstantCallSite(getProp);
    }
}
