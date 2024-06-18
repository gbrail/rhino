package org.mozilla.javascript.optimizer;

import java.util.OptionalInt;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.SlotMap;

public class DynamicOperations {
    public static Object getObjectProp(PropertyCallSite site, Object obj, Context cx, Scriptable scope) {
        if (obj instanceof ScriptableObject) {
            ScriptableObject so = (ScriptableObject)obj;
            OptionalInt found = site.getCacheIndex(so);
            if (found.isPresent()) {
                if (Bootstrapper.DEBUG) {
                    System.out.println("PROP " + site.getPropertyName() + " found cached");
                }
                // Found from cached index -- done
                return so.getFromCache(found.getAsInt(), so);
            }
            if (!site.isCacheFull()) {
                SlotMap.CacheableResult<Object> result = so.getWithCacheInfo(site.getPropertyName(), so);
                if (result != null) {
                    // Found and we can also cache it -- also done
                    if (Bootstrapper.DEBUG) {
                        System.out.println("PROP " + site.getPropertyName() + " caching");
                    }
                    site.cacheLocation(result);
                    return result.getValue();
                }
            }
        }
        // Otherwise, fallback.
        if (Bootstrapper.DEBUG) {
            System.out.println("PROP " + site.getPropertyName() + " fallback");
        }
        return ScriptRuntime.getObjectProp(obj, site.getPropertyName(), cx, scope);
    }
}
