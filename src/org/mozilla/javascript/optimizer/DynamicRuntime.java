/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.optimizer;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.util.concurrent.atomic.LongAdder;
import org.mozilla.classfile.ByteCode;
import org.mozilla.classfile.ClassFileWriter;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.SlotMap;

public class DynamicRuntime {
    protected static final LongAdder siteCount = new LongAdder();
    protected static final LongAdder initFastCount = new LongAdder();
    protected static final LongAdder initSlowCount = new LongAdder();
    protected static final LongAdder invokeFastCount = new LongAdder();
    protected static final LongAdder invokeFastFailCount = new LongAdder();

    protected static final boolean accumulateStats;

    public static final String BOOTSTRAP_SIGNATURE =
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;";

    public static final String GET_PROP_SIGNATURE =
            "(Ljava/lang/Object;Lorg/mozilla/javascript/Context;Lorg/mozilla/javascript/Scriptable;)Ljava/lang/Object;";
    public static final String SET_PROP_SIGNATURE =
            "(Ljava/lang/Object;Ljava/lang/Object;Lorg/mozilla/javascript/Context;Lorg/mozilla/javascript/Scriptable;)Ljava/lang/Object;";

    public static final ClassFileWriter.MHandle PROP_BOOTSTRAP_HANDLE =
            new ClassFileWriter.MHandle(
                    ByteCode.MH_INVOKESTATIC,
                    "org.mozilla.javascript.optimizer.DynamicRuntime",
                    "bootstrapPropertyOp",
                    DynamicRuntime.BOOTSTRAP_SIGNATURE);

    static {
        String propVal = System.getProperty("RhinoIndyStats");
        accumulateStats = propVal != null;
    }

    @SuppressWarnings("unused")
    public static CallSite bootstrapPropertyOp(
            MethodHandles.Lookup lookup, String name, MethodType mType)
            throws NoSuchMethodException, IllegalAccessException {
        if (accumulateStats) {
            siteCount.increment();
        }
        if (name.startsWith("GET:")) {
            String propertyName = name.substring(4);
            return bootstrapGetProperty(lookup, propertyName, true, mType);
        } else if (name.startsWith("GETNOWARN:")) {
            String propertyName = name.substring(10);
            return bootstrapGetProperty(lookup, propertyName, false, mType);
        } else if (name.startsWith("SET:")) {
            String propertyName = name.substring(4);
            return bootstrapSetProperty(lookup, propertyName, mType);
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

    public static CallSite bootstrapSetProperty(
            MethodHandles.Lookup lookup, String propertyName, MethodType mType)
            throws NoSuchMethodException, IllegalAccessException {
        SetObjectPropertySite site = new SetObjectPropertySite(mType);
        MethodType initType = mType.insertParameterTypes(0, String.class);
        site.invokeFast = lookup.findVirtual(SetObjectPropertySite.class, "invokeFast", initType);
        site.invokeFast = MethodHandles.insertArguments(site.invokeFast, 0, site, propertyName);
        site.fallback = lookup.findVirtual(SetObjectPropertySite.class, "invokeFallback", initType);
        site.fallback = MethodHandles.insertArguments(site.fallback, 0, site, propertyName);

        // The first method called is the "initialize" method, bound to the
        // call site itself so it can change its handle.
        MethodHandle init = lookup.findVirtual(SetObjectPropertySite.class, "initialize", initType);
        init = MethodHandles.insertArguments(init, 0, site, propertyName);
        site.setTarget(init);
        return site;
    }

    protected static final class GetObjectPropertySite extends MutableCallSite {
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
                    if (accumulateStats) {
                        initFastCount.increment();
                    }
                    return invokeFast(propertyName, allowWarn, obj, cx, scope);
                }
            }

            if (accumulateStats) {
                initSlowCount.increment();
            }
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
                    if (accumulateStats) {
                        invokeFastCount.increment();
                    }
                    return val;
                }
            }

            if (accumulateStats) {
                invokeFastFailCount.increment();
            }
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

    protected static final class SetObjectPropertySite extends MutableCallSite {
        MethodHandle fallback;
        MethodHandle invokeFast;
        SlotMap.FastKey fastKey;

        SetObjectPropertySite(MethodType mType) {
            super(mType);
        }

        /**
         * This is called first. If the target meets the requirements, it switches to a direct
         * invocation, and otherwise falls back to the generic method.
         */
        public Object initialize(
                String propertyName, Object obj, Object value, Context cx, Scriptable scope) {
            if (obj instanceof ScriptableObject) {
                ScriptableObject so = (ScriptableObject) obj;
                SlotMap.FastKey key = so.getFastKey(propertyName);
                if (key != null) {
                    fastKey = key;
                    setTarget(invokeFast);
                    if (accumulateStats) {
                        initFastCount.increment();
                    }
                    return invokeFast(propertyName, obj, value, cx, scope);
                }
            }

            if (accumulateStats) {
                initSlowCount.increment();
            }
            setTarget(fallback);
            return invokeFallback(propertyName, obj, value, cx, scope);
        }

        /** This is called every time with a fast property key. */
        public Object invokeFast(
                String propertyName, Object obj, Object value, Context cx, Scriptable scope) {
            if (obj instanceof ScriptableObject) {
                ScriptableObject so = ((ScriptableObject) obj);
                if (!so.isSealed() && so.putFast(fastKey, so, value)) {
                    if (accumulateStats) {
                        invokeFastCount.increment();
                    }
                    return value;
                }
            }

            if (accumulateStats) {
                invokeFastFailCount.increment();
            }
            return invokeFallback(propertyName, obj, value, cx, scope);
        }

        public Object invokeFallback(
                String propertyName, Object obj, Object value, Context cx, Scriptable scope) {
            return ScriptRuntime.setObjectProp(obj, propertyName, value, cx, scope);
        }
    }

    public static void printStats() {
        if (accumulateStats) {
            System.out.println("Call Sites:              " + siteCount.sum());
            System.out.println("Fast inits:              " + initFastCount.sum());
            System.out.println("Slow inits:              " + initSlowCount.sum());
            System.out.println("Fast invocations:        " + invokeFastCount.sum());
            System.out.println("Failed fast invocations: " + invokeFastFailCount.sum());
        }
    }
}
