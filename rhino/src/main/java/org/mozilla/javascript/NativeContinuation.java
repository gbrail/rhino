/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import java.util.Objects;

public final class NativeContinuation extends LambdaConstructor implements Function {
    private static final long serialVersionUID = 1794167133757605367L;

    private static final String CLASS_NAME = "Continuation";

    private Object implementation;

    public static void init(Context cx, Scriptable scope, boolean sealed) {
        NativeContinuation obj = new NativeContinuation(scope);
        ScriptableObject.defineProperty(scope, CLASS_NAME, obj, DONTENUM);
    }

    NativeContinuation(Scriptable scope) {
        super(scope, CLASS_NAME, 0, null, null);
    }

    public Object getImplementation() {
        return implementation;
    }

    public void initImplementation(Object implementation) {
        this.implementation = implementation;
    }

    @Override
    public String getClassName() {
        return CLASS_NAME;
    }

    @Override
    public Scriptable construct(Context cx, Scriptable scope, Object[] args) {
        throw Context.reportRuntimeError("Direct call is not supported");
    }

    @Override
    public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        return Interpreter.restartContinuation(this, cx, scope, args);
    }

    public static boolean isContinuationConstructor(Function f) {
        // In order for this to work, this class needs to be a subclass
        // of LambdaConstructor.
        return f instanceof NativeContinuation;
    }

    /**
     * Returns true if both continuations have equal implementations.
     *
     * @param c1 one continuation
     * @param c2 another continuation
     * @return true if the implementations of both continuations are equal, or they are both null.
     * @throws NullPointerException if either continuation is null
     */
    public static boolean equalImplementations(NativeContinuation c1, NativeContinuation c2) {
        return Objects.equals(c1.implementation, c2.implementation);
    }
}
