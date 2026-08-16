/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import java.util.HashSet;
import java.util.Set;
import org.mozilla.javascript.typedarrays.NativeArrayBuffer;

/**
 * Implements the WHATWG structured clone algorithm. Entry point for {@code structuredClone(value,
 * options)}.
 */
public final class StructuredClone {

    private StructuredClone() {}

    /**
     * Main entry point for structured cloning.
     *
     * @param cx current execution context
     * @param scope target scope for object creation
     * @param value the value to clone
     * @param options optional options object with "transfer" property
     * @return the cloned value
     */
    public static Object clone(Context cx, VarScope scope, Object value, Object options) {
        Set<NativeArrayBuffer> transferSet = new HashSet<>();

        if (options != null && !Undefined.isUndefined(options)) {
            parseTransfer(cx, scope, options, transferSet);
        }

        StructuredCloneContext ctx = new StructuredCloneContext(cx, scope, transferSet);
        Object result = ctx.clone(value);

        // Detach all transferred buffers
        for (NativeArrayBuffer buf : transferSet) {
            buf.detach();
        }

        return result;
    }

    private static void parseTransfer(
            Context cx, VarScope scope, Object options, Set<NativeArrayBuffer> transferSet) {
        if (!(options instanceof Scriptable)) {
            throw ScriptRuntime.typeErrorById("msg.arg.not.object", "options");
        }

        Object transferVal = ScriptableObject.getProperty((Scriptable) options, "transfer");
        if (transferVal == Scriptable.NOT_FOUND || Undefined.isUndefined(transferVal)) {
            return;
        }

        // Iterate the transfer list
        Object iterator = ScriptRuntime.callIterator(transferVal, cx, scope);
        try (IteratorLikeIterable it = new IteratorLikeIterable(cx, scope, iterator)) {
            for (Object item : it) {
                Object unwrapped = item;
                if (unwrapped instanceof Delegator) {
                    unwrapped = ((Delegator) unwrapped).getDelegee();
                }

                if (!(unwrapped instanceof NativeArrayBuffer)) {
                    throw ScriptRuntime.constructError(
                            "DataCloneError", "Transferable item is not an ArrayBuffer");
                }

                NativeArrayBuffer buf = (NativeArrayBuffer) unwrapped;

                if (buf.isDetached()) {
                    throw ScriptRuntime.constructError(
                            "DataCloneError", "Cannot transfer a detached ArrayBuffer");
                }

                if (!transferSet.add(buf)) {
                    throw ScriptRuntime.constructError(
                            "DataCloneError", "Duplicate ArrayBuffer in transfer list");
                }
            }
        }
    }
}
