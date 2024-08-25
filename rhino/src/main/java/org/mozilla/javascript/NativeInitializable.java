package org.mozilla.javascript;

public interface NativeInitializable {
    Object init(Context cx, Scriptable scope, boolean sealed);
}
