/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.mozilla.javascript.typedarrays.NativeArrayBuffer;

/**
 * Implements the WHATWG structured clone algorithm. Entry points: {@code structuredClone(value,
 * options)} for JavaScript ({@link #clone}) and {@link #cloneIntoScope} for Java embeddings that
 * need to move a graph of values into a different top-level scope.
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

    /**
     * Clones {@code value} with the same semantics as {@link #clone}, but creates every object in
     * the realm of {@code targetScope}: each clone's parent scope is set to the top level of {@code
     * targetScope}, and its prototype is looked up from that top level rather than from the realm
     * the source objects were created in.
     *
     * <p>This is a Java-only entry point, not exposed to JavaScript. It lets an embedding pass a
     * graph of values between scripts running in different top-level scopes: after cloning, the
     * graph behaves as if it had been created in the target scope, e.g. {@code instanceof} checks
     * and prototype method resolution succeed against the target realm's built-ins.
     *
     * <p>All {@code ArrayBuffer}s are copied, never moved. To move buffers instead, use {@link
     * #cloneIntoScope(Context, VarScope, Object, Collection)}.
     *
     * <p>Error values are re-created by looking up their type name (e.g. {@code "TypeError"}) in
     * the target realm, so non-standard error types resolvable only in the source realm will fail
     * to clone.
     *
     * @param cx the current execution context
     * @param targetScope the top-level scope (or any scope within it) whose realm the clones are
     *     wired into
     * @param value the value to clone
     * @return the cloned value
     */
    public static Object cloneIntoScope(Context cx, VarScope targetScope, Object value) {
        return cloneIntoScope(cx, targetScope, value, Set.of());
    }

    /**
     * As {@link #cloneIntoScope(Context, VarScope, Object)}, but additionally transfers the given
     * {@code ArrayBuffer}s: their storage is moved to the clones instead of being copied, and the
     * sources are detached once the clone succeeds. All views on a transferred buffer share the
     * single moved buffer in the clone. A listed buffer that is not reachable from {@code value} is
     * still detached, matching the transfer semantics of {@link #clone}; if the clone fails, no
     * buffer is detached.
     *
     * @param cx the current execution context
     * @param targetScope the top-level scope (or any scope within it) whose realm the clones are
     *     wired into
     * @param value the value to clone
     * @param transfer buffers to move rather than copy
     * @return the cloned value
     */
    public static Object cloneIntoScope(
            Context cx,
            VarScope targetScope,
            Object value,
            Collection<NativeArrayBuffer> transfer) {
        TopLevel target = ScriptableObject.getTopLevelScope(targetScope);
        Set<NativeArrayBuffer> transferSet = new HashSet<>(transfer);
        StructuredCloneContext ctx = new StructuredCloneContext(cx, target, transferSet);
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

                if (!(unwrapped instanceof NativeArrayBuffer buf)) {
                    throw ScriptRuntime.constructErrorById(
                            "DataCloneError", "msg.clone.transfer.not.arraybuffer");
                }

                if (buf.isDetached()) {
                    throw ScriptRuntime.constructErrorById(
                            "DataCloneError", "msg.clone.transfer.detached");
                }

                if (!transferSet.add(buf)) {
                    throw ScriptRuntime.constructErrorById(
                            "DataCloneError", "msg.clone.transfer.duplicate");
                }
            }
        }
    }
}
