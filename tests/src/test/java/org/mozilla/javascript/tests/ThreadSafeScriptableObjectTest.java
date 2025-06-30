package org.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.ScriptableObject;

/*
 * This test can only run in the "tests" directory because setting the global context
 * factory affects other tests running in the same JVM, and only the "tests" directory
 * runs tests in separate JVMs.
 */
public class ThreadSafeScriptableObjectTest {
    private ContextFactory oldGlobal;

    @Test
    public void canSealGlobalObjectWithoutDeadlock() {
        ContextFactory.getGlobalSetter()
                .setContextFactoryGlobal(
                        new ContextFactory() {
                            @Override
                            protected boolean hasFeature(Context cx, int featureIndex) {
                                if (featureIndex == Context.FEATURE_THREAD_SAFE_OBJECTS) {
                                    return true;
                                }
                                return super.hasFeature(cx, featureIndex);
                            }
                        });

        try (Context cx = Context.enter()) {
            ScriptableObject global = cx.initStandardObjects();
            global.sealObject();

            // Registered by NativeJavaTopPackage
            assertNotNull(global.get("Packages", global));
        }
    }
}
