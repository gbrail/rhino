/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.optimizer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import jdk.dynalink.NamedOperation;
import jdk.dynalink.NamespaceOperation;
import jdk.dynalink.Operation;
import jdk.dynalink.StandardNamespace;
import jdk.dynalink.StandardOperation;
import jdk.dynalink.linker.GuardedInvocation;
import jdk.dynalink.linker.GuardingDynamicLinker;
import jdk.dynalink.linker.LinkRequest;
import jdk.dynalink.linker.LinkerServices;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.SlotMap;

/**
 * This is a linker for property operations -- it works with the "fast key" support in
 * ScriptableObject to optimize objects for operations that can shortcut the property table.
 */
public class PropertyLinker implements GuardingDynamicLinker {
    @Override
    public GuardedInvocation getGuardedInvocation(LinkRequest req, LinkerServices svcs)
            throws NoSuchMethodException, IllegalAccessException {
        Operation namedOp = req.getCallSiteDescriptor().getOperation();
        if (!(namedOp instanceof NamedOperation)) {
            return null;
        }
        String propertyName = (String) ((NamedOperation) namedOp).getName();

        Operation nsOp = ((NamedOperation) namedOp).getBaseOperation();
        if (!(nsOp instanceof NamespaceOperation)) {
            return null;
        }
        if (((NamespaceOperation) nsOp).getNamespace(0) != StandardNamespace.PROPERTY) {
            return null;
        }

        MethodType mType = req.getCallSiteDescriptor().getMethodType();
        MethodHandles.Lookup lookup = req.getCallSiteDescriptor().getLookup();
        Operation op = ((NamespaceOperation) nsOp).getBaseOperation();

        if ((op == StandardOperation.GET
                        || op == DynamicRuntime.RhinoOperation.GETNOWARN
                        || op == StandardOperation.SET)
                && req.getReceiver() instanceof ScriptableObject) {
            ScriptableObject so = (ScriptableObject) req.getReceiver();
            SlotMap.FastKey key = so.getFastKey(propertyName);
            if (key != null && (op != StandardOperation.SET || so.isFastKeyValidForPut(key))) {
                MethodType guardType =
                        mType.changeReturnType(Boolean.TYPE)
                                .insertParameterTypes(0, SlotMap.FastKey.class);
                MethodHandle rawGuard =
                        lookup.findStatic(
                                PropertyLinker.class,
                                op == StandardOperation.SET ? "guardSetFastKey" : "guardGetFastKey",
                                guardType);
                MethodHandle guard = MethodHandles.insertArguments(rawGuard, 0, key);

                MethodType invokeType = mType.insertParameterTypes(0, SlotMap.FastKey.class);
                MethodHandle rawInvoke =
                        lookup.findStatic(
                                PropertyLinker.class,
                                op == StandardOperation.SET
                                        ? "invokeSetFastKey"
                                        : "invokeGetFastKey",
                                invokeType);
                MethodHandle invoke = MethodHandles.insertArguments(rawInvoke, 0, key);

                if (DynamicRuntime.DEBUG) {
                    if (op == StandardOperation.SET) {
                        System.out.println(namedOp + " -> fast SET: " + key);
                    } else {
                        System.out.println(namedOp + " -> fast GET: " + key);
                    }
                }

                return new GuardedInvocation(invoke, guard);
            }
        }

        // Let another linker pick this up
        return null;
    }

    public static boolean guardGetFastKey(
            SlotMap.FastKey key, Object target, Context cx, Scriptable start) {
        if (target instanceof ScriptableObject) {
            return ((ScriptableObject) target).isFastKeyValid(key);
        }
        return false;
    }

    public static Object invokeGetFastKey(
            SlotMap.FastKey key, Object target, Context cx, Scriptable start) {
        ScriptableObject so = (ScriptableObject) target;
        return so.getFast(key, so);
    }

    public static boolean guardSetFastKey(
            SlotMap.FastKey key, Object target, Object value, Context cx, Scriptable start) {
        if (target instanceof ScriptableObject) {
            return ((ScriptableObject) target).isFastKeyValidForPut(key);
        }
        return false;
    }

    public static Object invokeSetFastKey(
            SlotMap.FastKey key, Object target, Object value, Context cx, Scriptable start) {
        ScriptableObject so = (ScriptableObject) target;
        so.putFast(key, so, value);
        return value;
    }
}
