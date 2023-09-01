package org.mozilla.javascript;

import java.util.WeakHashMap;

/**
 * This class maintains a global pool of identifiers. It is global and thread-safe. This makes
 * identifiers relatively expensive to create and look up, so they should only be created
 * infrequently, such as call site bootstrap or Java class initialization.
 */
public class Identifiers {
    private static final Identifiers self = new Identifiers();

    private final WeakHashMap<String, StringKey> identifiers = new WeakHashMap<>();
    private long lastId = 0;

    private Identifiers() {}

    public static final Identifiers get() {
        return self;
    }

    public synchronized StringKey create(String name) {
        return identifiers.computeIfAbsent(
                name,
                n -> {
                    long id = ++lastId;
                    return new StringKey(name, id);
                });
    }
}
