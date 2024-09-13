package org.mozilla.javascript.xmlimpl;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.LoadableFeature;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

public class XMLFeature implements LoadableFeature{
    @Override
    public void loadRhinoFeature(Context cx, ScriptableObject scope, boolean sealed) {
        if (cx.hasFeature(Context.FEATURE_E4X)) {
            // First, create or look up a singleton XMLLibImpl
            // Then, use it in all of the initializers below... 
            ScriptRuntime.initializeNative(cx, scope, "XML", sealed, XMLLibImpl::initXML); 
            ScriptRuntime.initializeNative(cx, scope, "XMLLIst", sealed, XMLLibImpl::initXML); 
            ScriptRuntime.initializeNative(cx, scope, "Namespace", sealed, XMLLibImpl::initNamespace); 
            ScriptRuntime.initializeNative(cx, scope, "QName", sealed, XMLLibImpl::initQName);
        }
    }
}
