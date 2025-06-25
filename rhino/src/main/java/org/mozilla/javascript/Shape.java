package org.mozilla.javascript;

import java.util.Objects;

/** A Shape represents a node in a tree of object shape transitions. */
public class Shape {
    private final Object property;
    private final Shape parent;
    private final int index;

    private ShapeNode[] children;
    private PropNode[] properties;

    /** Create an empty Shape. */
    public Shape() {
        this.property = null;
        this.parent = null;
        this.index = -1;
    }

    private Shape(Object property, Shape parent, int index) {
        this.property = property;
        this.parent = parent;
        this.index = index;
        if (property != null) {
            buildPropertyMap();
        }
    }

    /** Find the specified key in the property map, and if not found, then return -1. */
    public int get(Object key) {
        return findProperty(key);
    }

    /**
     * Find the specified key in the property map. If found, return the result with the new index.
     * If not, then transition to a new shape that contains the key and return it.
     */
    public PutResult putIfAbsent(Object key) {
        int index = findProperty(key);
        if (index >= 0) {
            return new PutResult(index, null);
        }

        var newShape = putChildIfAbsent(key);
        return new PutResult(newShape.index, newShape);
    }

    private int findProperty(Object key) {
        if (properties == null) {
            return -1;
        }
        int hashCode = key.hashCode();
        int bucket = hashObject(hashCode, properties.length);
        for (var n = properties[bucket]; n != null; n = n.next) {
            if (hashCode == n.hashCode && Objects.equals(n.key, key)) {
                return n.index;
            }
        }
        return -1;
    }

    private void buildPropertyMap() {
        int numSlots = calculateMapSize(index + 1);
        properties = new PropNode[numSlots];
        putProperty(property, index);
        Shape p = parent;
        while (p.index >= 0) {
            putProperty(p.property, p.index);
            p = p.parent;
        }
    }

    private void putProperty(Object key, int index) {
        int hash = hashObject(key.hashCode(), properties.length);
        var n = new PropNode(key, index);
        n.next = properties[hash];
        properties[hash] = n;
    }

    private Shape putChildIfAbsent(Object key) {
        int hashCode = key.hashCode();
        if (children != null) {
            int bucket = hashObject(hashCode, children.length);
            for (var c = children[bucket]; c != null; c = c.next) {
                if (hashCode == c.hashCode && Objects.equals(key, c.key)) {
                    return c.shape;
                }
            }
        }

        if (children == null) {
            children = new ShapeNode[2];
        } else if (4 * (index + 1) > 3 * children.length) {
            var newChildren = new ShapeNode[children.length * 2];
            rehashChildren(children, newChildren);
            children = newChildren;
        }
        var newShape = new Shape(key, this, index + 1);
        var newNode = new ShapeNode(key, newShape);
        int bucket = hashObject(hashCode, children.length);
        newNode.next = children[bucket];
        children[bucket] = newNode;
        return newShape;
    }

    private static void rehashChildren(ShapeNode[] oldShapes, ShapeNode[] newShapes) {
        for (ShapeNode o : oldShapes) {
            while (o != null) {
                int bucket = hashObject(o.hashCode, newShapes.length);
                var nn = new ShapeNode(o.key, o.shape);
                nn.next = newShapes[bucket];
                newShapes[bucket] = nn;
                o = o.next;
            }
        }
    }

    /** Calculate power of two that leaves map no more than 3/4 full. */
    private static int calculateMapSize(int size) {
        int needed = Math.max((size * 4) / 3, 2);
        return 1 << (32 - Integer.numberOfLeadingZeros(needed - 1));
    }

    private static int hashObject(int hashCode, int numSlots) {
        // Use bit mixing for a more even hash code, as suggested by GPT-4.1.
        return (hashCode ^ (hashCode >>> 16)) & (numSlots - 1);
    }

    @Override
    public String toString() {
        int buckets = properties == null ? 0 : properties.length;
        return "Shape{property = \""
                + property
                + "\" index = "
                + index
                + " buckets = "
                + buckets
                + "}";
    }

    public static final class PutResult {
        private final int index;
        private final Shape shape;

        PutResult(int index, Shape shape) {
            this.index = index;
            this.shape = shape;
        }

        public int getIndex() {
            return index;
        }

        public Shape getShape() {
            return shape;
        }

        public boolean isNewShape() {
            return shape != null;
        }
    }

    private static final class PropNode {
        private final Object key;
        private final int hashCode;
        private final int index;
        private PropNode next;

        PropNode(Object key, int index) {
            this.key = key;
            this.hashCode = key.hashCode();
            this.index = index;
        }
    }

    private static final class ShapeNode {
        private final Object key;
        private final int hashCode;
        private final Shape shape;
        private ShapeNode next;

        ShapeNode(Object key, Shape shape) {
            this.key = key;
            this.hashCode = key.hashCode();
            this.shape = shape;
        }
    }
}
