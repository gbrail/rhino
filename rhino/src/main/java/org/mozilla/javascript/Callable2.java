package org.mozilla.javascript;

/** This is a single-function interface for use in lambda functions that take one argument. */
public interface Callable2 {
    Object call(Context cx, Scriptable scope, Scriptable thisObj, Object arg1, Object arg2);
}
