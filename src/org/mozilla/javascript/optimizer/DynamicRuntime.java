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

    public static final ClassFileWriter.MHandle BOOTSTRAP =
            new ClassFileWriter.MHandle(
                    ByteCode.MH_INVOKESTATIC,
                    "org.mozilla.javascript.optimizer.DynamicRuntime",
                    "bootstrap",
                    DynamicRuntime.BOOTSTRAP_SIGNATURE);

    @SuppressWarnings("unused")
    public static CallSite bootstrap(MethodHandles.Lookup lookup, String name, MethodType mType)
            throws NoSuchMethodException, IllegalAccessException {
        if (name.startsWith("GET:")) {
            String propertyName = name.substring(4);
            return bootstrapGetProp(lookup, propertyName, mType);
        } else if (name.startsWith("SET:")) {
            String propertyName = name.substring(4);
            return bootstrapSetProp(lookup, propertyName, mType);
        } else {
            throw new NoSuchMethodException(name);
        }
    }

    private static CallSite bootstrapGetProp(
            MethodHandles.Lookup lookup, String name, MethodType mType)
            throws NoSuchMethodException, IllegalAccessException {
        // Insert a String parameter to the list of parameters we're going to call
        MethodType getType = mType.insertParameterTypes(1, String.class);
        MethodHandle getProp = lookup.findStatic(ScriptRuntime.class, "getObjectProp", getType);
        // Always call with the "name" as the string parameter
        getProp = MethodHandles.insertArguments(getProp, 1, name);
        return new ConstantCallSite(getProp);
    }

    private static CallSite bootstrapSetProp(
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
