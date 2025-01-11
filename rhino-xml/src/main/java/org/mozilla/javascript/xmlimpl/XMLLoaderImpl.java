package org.mozilla.javascript.xmlimpl;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.xml.XMLLib;
import org.mozilla.javascript.xml.XMLLoader;

public class XMLLoaderImpl implements XMLLoader {
    @Override
    public void load(ScriptableObject scope, boolean sealed) {
        scope.addLazilyInitializedValue(
                "XML",
                0,
                (Context lcx, Scriptable lscope, boolean lsealed) ->
                        loadXML(lcx, lscope, "XML", sealed),
                ScriptableObject.DONTENUM,
                sealed);
        scope.addLazilyInitializedValue(
                "XMLList",
                0,
                (Context lcx, Scriptable lscope, boolean lsealed) ->
                        loadXML(lcx, lscope, "XMLList", sealed),
                ScriptableObject.DONTENUM,
                sealed);
        scope.addLazilyInitializedValue(
                "Namespace",
                0,
                (Context lcx, Scriptable lscope, boolean lsealed) ->
                        loadXML(lcx, lscope, "Namespace", sealed),
                ScriptableObject.DONTENUM,
                sealed);
        scope.addLazilyInitializedValue(
                "QName",
                0,
                (Context lcx, Scriptable lscope, boolean lsealed) ->
                        loadXML(lcx, lscope, "QName", sealed),
                ScriptableObject.DONTENUM,
                sealed);
    }

    @Override
    public XMLLib.Factory getFactory() {
        return XMLLib.Factory.create(XMLLibImpl.class.getName());
    }

    private static Object loadXML(Context cx, Scriptable scope, String name, boolean sealed) {
        XMLLibImpl.init(cx, scope, sealed);
        return scope.get(name, scope);
    }
}
