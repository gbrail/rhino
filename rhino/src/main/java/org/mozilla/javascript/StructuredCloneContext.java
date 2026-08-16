/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import java.util.IdentityHashMap;
import java.util.Set;
import org.mozilla.javascript.annotations.JSStructuredCloneable;
import org.mozilla.javascript.typedarrays.NativeArrayBuffer;

/**
 * Holds state for a structured-clone operation: cycle-tracking memory, transfer set, and target
 * scope.
 */
public final class StructuredCloneContext {

    private final Context cx;
    private final VarScope scope;
    private final IdentityHashMap<Object, Object> memory;
    private final Set<NativeArrayBuffer> transferSet;

    public StructuredCloneContext(Context cx, VarScope scope, Set<NativeArrayBuffer> transferSet) {
        this.cx = cx;
        this.scope = scope;
        this.memory = new IdentityHashMap<>();
        this.transferSet = transferSet;
    }

    /** Clone {@code value}, handling primitives, cycles, and object dispatch. */
    public Object clone(Object value) {
        if (value == null || Undefined.isUndefined(value)) {
            return value;
        }

        // Primitives pass through directly (BigInteger is the BigInt primitive)
        if (value instanceof Boolean || value instanceof Number || value instanceof String) {
            return value;
        }

        // Symbol is never cloneable
        if (value instanceof NativeSymbol) {
            throw dataCloneErrorById("msg.clone.not.symbol");
        }

        // Functions and other callables are never cloneable
        if (value instanceof Callable) {
            throw dataCloneErrorById("msg.clone.not.function");
        }

        // Unwrap Delegator
        if (value instanceof Delegator) {
            value = ((Delegator) value).getDelegee();
        }

        // Cycle detection
        Object existing = memory.get(value);
        if (existing != null) {
            return existing;
        }

        if (value instanceof ScriptableObject so) {
            Class<?> cls = so.getClass();
            while (cls != null) {
                if (cls.isAnnotationPresent(JSStructuredCloneable.class)) {
                    break;
                }
                cls = cls.getSuperclass();
            }
            if (cls == null) {
                throw dataCloneErrorById("msg.clone.not.cloneable", so.getClassName());
            }

            return so.structuredClone(this);
        }

        throw dataCloneErrorById("msg.clone.not.supported");
    }

    /** Record a source-to-clone mapping in the cycle-tracking memory. */
    public void remember(Object source, Object clone) {
        memory.put(source, clone);
    }

    /** Returns true if any buffer has been transferred during this clone operation. */
    public boolean hasTransferredBuffers() {
        return !transferSet.isEmpty();
    }

    /** Get the current execution context. */
    public Context getContext() {
        return cx;
    }

    /** Get the target scope for object creation. */
    public VarScope getScope() {
        return scope;
    }

    /** Get the transfer set for checking whether a buffer should be transferred. */
    public Set<NativeArrayBuffer> getTransferSet() {
        return transferSet;
    }

    public EcmaError dataCloneErrorById(String messageId, Object... args) {
        return ScriptRuntime.constructErrorById("DataCloneError", messageId, args);
    }
}
