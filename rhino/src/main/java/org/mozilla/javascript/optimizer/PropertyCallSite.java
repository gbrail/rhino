package org.mozilla.javascript.optimizer;

import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import org.mozilla.javascript.PropertyMap;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.SlotMap;

public class PropertyCallSite extends MutableCallSite {
    private final String propertyName;
    private PropertyMap cachedMap1;
    private int cachedIndex1;

    public PropertyCallSite(String name, MethodType type) {
        super(type);
        propertyName = name;
        DynamicOperations.callSites.increment();
    }

    public boolean isCacheFull() {
        return cachedMap1 != null;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public int getCacheIndex(ScriptableObject so) {
        if (cachedMap1 == so.getPropertyMap()) {
            if (Bootstrapper.DEBUG) {
                System.out.println("PROP " + propertyName + ": GET IX " + cachedIndex1);
            }
            return cachedIndex1;
        }
        return -1;
    }

    public void cacheLocation(SlotMap.CacheableResult<Object> result) {
        if (result.getPropertyMap() != null && cachedMap1 == null) {
            if (Bootstrapper.DEBUG) {
                System.out.println("PROP " + propertyName + ": CACHE IX " + result.getIndex());
            }
            cachedMap1 = result.getPropertyMap();
            cachedIndex1 = result.getIndex();
        }
    }
}
