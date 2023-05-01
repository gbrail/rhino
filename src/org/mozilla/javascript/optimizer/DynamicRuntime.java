/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.optimizer;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.util.concurrent.atomic.LongAccumulator;
import org.mozilla.classfile.ByteCode;
import org.mozilla.classfile.ClassFileWriter;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.SlotMap;

public class DynamicRuntime {
    protected static LongAccumulator siteCount = new LongAccumulator(Long::sum, 0);
    protected static LongAccumulator initFastCount = new LongAccumulator(Long::sum, 0);
    protected static LongAccumulator initSlowCount = new LongAccumulator(Long::sum, 0);
    protected static LongAccumulator invokeFastCount = new LongAccumulator(Long::sum, 0);
    protected static LongAccumulator invokeFastFailCount = new LongAccumulator(Long::sum, 0);

    public static final String BOOTSTRAP_SIGNATURE =
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;";

    public static final String GET_PROP_SIGNATURE =
            "(Ljava/lang/Object;Lorg/mozilla/javascript/Context;Lorg/mozilla/javascript/Scriptable;)Ljava/lang/Object;";

    public static final ClassFileWriter.MHandle PROP_BOOTSTRAP_HANDLE =
            new ClassFileWriter.MHandle(
                    ByteCode.MH_INVOKESTATIC,
                    "org.mozilla.javascript.optimizer.DynamicRuntime",
                    "bootstrapPropertyOp",
                    DynamicRuntime.BOOTSTRAP_SIGNATURE);

    @SuppressWarnings("unused")
    public static CallSite bootstrapPropertyOp(
            MethodHandles.Lookup lookup, String name, MethodType mType)
            throws NoSuchMethodException, IllegalAccessException {
        siteCount.accumulate(1);
        if (name.startsWith("GET:")) {
            String propertyName = name.substring(4);
            return bootstrapGetProperty(lookup, propertyName, true, mType);
        } else if (name.startsWith("GETNOWARN:")) {
            String propertyName = name.substring(10);
            return bootstrapGetProperty(lookup, propertyName, false, mType);
        } else {
            throw new NoSuchMethodException(name);
        }
    }

    public static CallSite bootstrapGetProperty(
            MethodHandles.Lookup lookup, String propertyName, boolean allowNoWarn, MethodType mType)
            throws NoSuchMethodException, IllegalAccessException {
        GetObjectPropertySite site = new GetObjectPropertySite(mType);
        MethodType initType = mType.insertParameterTypes(0, String.class, Boolean.TYPE);
        site.invokeFast = lookup.findVirtual(GetObjectPropertySite.class, "invokeFast", initType);
        site.invokeFast =
                MethodHandles.insertArguments(site.invokeFast, 0, site, propertyName, allowNoWarn);
        site.fallback = lookup.findVirtual(GetObjectPropertySite.class, "invokeFallback", initType);
        site.fallback =
                MethodHandles.insertArguments(site.fallback, 0, site, propertyName, allowNoWarn);

        // The first method called is the "initialize" method, bound to the
        // call site itself so it can change its handle.
        MethodHandle init = lookup.findVirtual(GetObjectPropertySite.class, "initialize", initType);
        init = MethodHandles.insertArguments(init, 0, site, propertyName, allowNoWarn);
        site.setTarget(init);
        return site;
    }

    public static final class GetObjectPropertySite extends MutableCallSite {
        MethodHandle fallback;
        MethodHandle invokeFast;
        SlotMap.FastKey fastKey;

        GetObjectPropertySite(MethodType mType) {
            super(mType);
        }

        /**
         * This is called first. If the target meets the requirements, it switches to a direct
         * invocation, and otherwise falls back to the generic method.
         */
        public Object initialize(
                String propertyName, boolean allowWarn, Object obj, Context cx, Scriptable scope) {
            if (obj instanceof ScriptableObject) {
                SlotMap.FastKey key = ((ScriptableObject) obj).getFastKey(propertyName);
                if (key != null) {
                    fastKey = key;
                    setTarget(invokeFast);
                    initFastCount.accumulate(1);
                    return invokeFast(propertyName, allowWarn, obj, cx, scope);
                }
            }

            initSlowCount.accumulate(1);
            setTarget(fallback);
            return invokeFallback(propertyName, allowWarn, obj, cx, scope);
        }

        /** This is called every time with a fast property key. */
        public Object invokeFast(
                String propertyName, boolean allowWarn, Object obj, Context cx, Scriptable scope) {
            if (obj instanceof ScriptableObject) {
                ScriptableObject so = ((ScriptableObject) obj);
                Object val = so.getFast(fastKey, so);
                if (val != SlotMap.NOT_A_FAST_PROPERTY) {
                    invokeFastCount.accumulate(1);
                    return val;
                }
            }

            invokeFastFailCount.accumulate(1);
            return invokeFallback(propertyName, allowWarn, obj, cx, scope);
        }

        public Object invokeFallback(
                String propertyName, boolean allowWarn, Object obj, Context cx, Scriptable scope) {
            if (allowWarn) {
                return ScriptRuntime.getObjectProp(obj, propertyName, cx, scope);
            }
            return ScriptRuntime.getObjectPropNoWarn(obj, propertyName, cx, scope);
        }
    }

    public static void printStats() {
        System.out.println("Call Sites:              " + siteCount.get());
        System.out.println("Fast inits:              " + initFastCount.get());
        System.out.println("Slow inits:              " + initSlowCount.get());
        System.out.println("Fast invocations:        " + invokeFastCount.get());
        System.out.println("Failed fast invocations: " + invokeFastFailCount.get());
    }
}
