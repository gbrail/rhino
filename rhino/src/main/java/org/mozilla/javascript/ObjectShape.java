package org.mozilla.javascript;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.WeakHashMap;

public class ObjectShape {
    private final int level;
    private final ObjectShape parent;
    private final EmbeddedMap<Object, Integer> indices;
    private final WeakHashMap<Object, ObjectShape> children = new WeakHashMap<>();

    public ObjectShape() {
        level = -1;
        parent = null;
        indices = new EmbeddedMap<>();
    }

    public ObjectShape(ObjectShape parent, Object newKey) {
        this.level = parent.level + 1;
        this.parent = parent;
        indices = new EmbeddedMap<>(parent.indices);
        indices.put(newKey, this.level);
    }

    /** Return the parent of this map. */
    public ObjectShape getParent() {
        return parent;
    }

    public int getLevel() {
        return level;
    }

    /** Return the index of the specified property key in the current shape. */
    public OptionalInt getProperty(Object key) {
        Integer ix = indices.get(key);
        return ix == null ? OptionalInt.empty() : OptionalInt.of(ix);
    }

    /**
     * Return the object shape that contains the specified property key. For a given tree of shapes,
     * the implementation guarantees that there will only be a single shape for any ordered list of
     * property keys.
     */
    public Result putProperty(Object key) {
        Integer ix = indices.get(key);
        if (ix != null) {
            // Index already exists
            return new Result(ix);
        }

        // Missing, so put a new child in the map if necessary
        ObjectShape child = children.computeIfAbsent(key, kk -> new ObjectShape(this, key));
        assert (child.level == indices.size());
        return new Result(child.level, child);
    }

    public static final class Result {
        private final int index;
        private final Optional<ObjectShape> shape;

        Result(int index) {
            this.index = index;
            this.shape = Optional.empty();
        }

        Result(int index, ObjectShape shape) {
            this.index = index;
            this.shape = Optional.of(shape);
        }

        public int getIndex() {
            return index;
        }

        public Optional<ObjectShape> getShape() {
            return shape;
        }
    }
}
