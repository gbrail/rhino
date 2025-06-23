package org.mozilla.javascript;

import java.util.Objects;

/**
 * A Shape represents a node in a tree of object shape transitions.
 */
public class Shape {
    private final Object property;
    private final Shape parent;
    private final int index;

    private ShapeNode[] children;
    private PropNode[] properties;

    public static final Shape EMPTY = new Shape(null, null, -1);

    private Shape(Object property, Shape parent, int index) {
        this.property = property;
        this.parent = parent;
        this.index = index;
        if (property != null) {
            buildPropertyMap();
        }
    }

    /**
     * Find the specified key in the property map, and if not found, then
     * return -1;
     */
    public int get(Object key) {
        if (properties == null) {
            return -1;
        }
        int hash = hashObject(key, properties.length);
        for (var n = properties[hash]; n != null; n = n.next) {
            if (Objects.equals(n.key, key)) {
                return n.index;
            }
        }
        return -1;
    }

    /**
     * Find the specified key in the property map. If found, return the
     * result with the new index. If not, then transition to a new 
     * shape that contains the key and return it.
     */
    public PutResult putIfAbsent(Object key) {
        if (properties != null) {
            int hash = hashObject(key, properties.length);
            var n = properties[hash];
            while (n != null && !Objects.equals(key, n.key)) {
                n = n.next;
            }
            if (n != null) {
                return new PutResult(n.index, null);
            }
        }

        var newShape = putChildIfAbsent(key);
        return new PutResult(newShape.index, newShape);
    }

    private void buildPropertyMap() {
        int numSlots = calculateMapSize(index + 1);
        properties = new PropNode[numSlots];
        putProperty(property, index);
        Shape p = parent;
        while (p != EMPTY) {
            putProperty(p.property, p.index);
            p = p.parent;
        }
    }

    private void putProperty(Object key, int index) {
        int hash = hashObject(key, properties.length);
        var n = new PropNode(key, index);
        n.next = properties[hash];
        properties[hash] = n;
    }

    private Shape putChildIfAbsent(Object key) {
        if (children != null) {
            int hash = hashObject(key, children.length);
            var c = children[hash];
            while (c != null && !Objects.equals(key, c.key)) {
                c = c.next;
            }
            if (c != null) {
                return c.shape;
            }
            // TODO if map is not too big, then insert it right here
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
        int hash = hashObject(key, children.length);
        newNode.next = children[hash];
        children[hash] = newNode;
        return newShape;
    }

    private static void rehashChildren(ShapeNode[] oldShapes, ShapeNode[] newShapes) {
        for (ShapeNode o : oldShapes) {
            while (o != null) {
                int hash = hashObject(o.key, newShapes.length);
                var nn = new ShapeNode(o.key, o.shape);
                nn.next = newShapes[hash];
                newShapes[hash] = nn;
                o = o.next;
            }
        }
    }

    /**
     * Calculate power of two that leaves map no more than 3/4 full.
     */
    private static int calculateMapSize(int size) {
        int s = 2;
        while (4 * size > 3 * s) {
            s *= 2;
        }
        return s;
    }

    private static int hashObject(Object key, int numSlots) {
        return key.hashCode() & (numSlots - 1);
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
        private final int index;
        private PropNode next;

        PropNode(Object key, int index) {
            this.key = key;
            this.index = index;
        }
    }

    private static final class ShapeNode {
        private final Object key;
        private final Shape shape;
        private ShapeNode next;

        ShapeNode(Object key, Shape shape) {
            this.key = key;
            this.shape = shape;
        }
    }
}
