package org.mozilla.javascript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

public class PropertyMapTest {
    @Test
    public void createTree() {
        ObjectShape root = ObjectShape.emptyMap();
        assertEquals(-1, root.getPosition());
        assertEquals(-1, root.find("one"));
        assertEquals(-1, root.find("two"));
        assertEquals(-1, root.find("three"));
        assertEquals(-1, root.find("four"));

        // New maps should progressively build in order
        ObjectShape one = root.add("one");
        assertNotNull(one);
        assertEquals(0, one.getPosition());
        ObjectShape two = one.add("two");
        assertNotNull(two);
        assertEquals(1, two.getPosition());
        ObjectShape three = two.add("three");
        assertNotNull(three);
        assertEquals(2, three.getPosition());

        // Make "four" a branch of "one"
        ObjectShape four = one.add("four");
        assertNotNull(four);
        assertEquals(1, four.getPosition());

        // Verify all the indices
        assertEquals(0, one.find("one"));
        assertEquals(-1, one.find("two"));
        assertEquals(-1, one.find("three"));
        assertEquals(-1, one.find("four"));
        assertEquals(0, two.find("one"));
        assertEquals(1, two.find("two"));
        assertEquals(-1, two.find("three"));
        assertEquals(-1, two.find("four"));
        assertEquals(0, three.find("one"));
        assertEquals(1, three.find("two"));
        assertEquals(2, three.find("three"));
        assertEquals(-1, three.find("four"));
        assertEquals(0, four.find("one"));
        assertEquals(-1, four.find("two"));
        assertEquals(-1, four.find("three"));
        assertEquals(1, four.find("four"));

        // Traverse the tree again, should get the same objects
        ObjectShape one1 = root.add("one");
        assertSame(one1, one);
        ObjectShape two2 = one1.add("two");
        assertSame(two2, two);
        ObjectShape three3 = two2.add("three");
        assertSame(three3, three);
    }
}
