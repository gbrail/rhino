package org.mozilla.javascript;

/** This is a single-function interface for use in lambda functions that take no arguments. */
public interface Callable0 {
    Object call(Context cx, Scriptable scope, Scriptable thisObj);
}
