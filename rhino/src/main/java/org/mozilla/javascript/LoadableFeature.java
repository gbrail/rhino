package org.mozilla.javascript;

public interface LoadableFeature {
    void loadRhinoFeature(Context cx, ScriptableObject scope, boolean sealed);
}
