package org.mozilla.javascript.optimizer;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.concurrent.atomic.LongAdder;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.SlotMap;

public class DynamicOperations {
    static final boolean STATISTICS = true;

    static final LongAdder callSites = new LongAdder();
    static final LongAdder fastCalls = new LongAdder();
    static final LongAdder cacheFills = new LongAdder();
    static final LongAdder fallbackCalls = new LongAdder();

    public static Object getObjectProp(
            PropertyCallSite site, Object obj, Context cx, Scriptable scope) {
        if (obj instanceof ScriptableObject) {
            ScriptableObject so = (ScriptableObject) obj;
            int foundIndex = site.getCacheIndex(so);
            if (foundIndex >= 0) {
                if (Bootstrapper.DEBUG) {
                    System.out.println("PROP " + site.getPropertyName() + " found cached");
                }
                // Found from cached index -- done
                if (STATISTICS) {
                    fastCalls.increment();
                }
                return so.getFromCache(foundIndex, so);
            }
            if (!site.isCacheFull()) {
                SlotMap.CacheableResult<Object> result =
                        so.getWithCacheInfo(site.getPropertyName(), so);
                if (result != null) {
                    // Found and we can also cache it -- also done
                    if (Bootstrapper.DEBUG) {
                        System.out.println("PROP " + site.getPropertyName() + " caching");
                    }
                    site.cacheLocation(result);
                    if (STATISTICS) {
                        cacheFills.increment();
                    }
                    return result.getValue();
                }
            }
        }
        // Otherwise, fallback.
        if (Bootstrapper.DEBUG) {
            System.out.println("PROP " + site.getPropertyName() + " fallback");
        }
        if (STATISTICS) {
            fallbackCalls.increment();
        }
        return ScriptRuntime.getObjectProp(obj, site.getPropertyName(), cx, scope);
    }

    public static final void dump(OutputStream out) {
        PrintWriter w = new PrintWriter(out);
        w.println("*** Call site stats ***");
        w.println("Call sites:            " + callSites.sum());
        w.println("Fast invocations:      " + fastCalls.sum());
        w.println("Cache fills:           " + cacheFills.sum());
        w.println("Fallback calls:        " + fallbackCalls.sum());
        w.println("***");
        w.flush();
    }
}
