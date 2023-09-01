package org.mozilla.javascript;

import java.io.Serializable;
import java.util.Objects;

/**
 * A StringKey is a key that may be a string, but it also may be a pre-defined identifier, created
 * by the Identifiers class. In that second case, the key can be compared more quickly.
 */
public class StringKey implements Comparable<StringKey>, Serializable {
    private final String s;
    private final long id;
    private final int hash;

    public StringKey(String s) {
        this.s = s;
        this.id = 0;
        this.hash = s == null ? 0 : s.hashCode();
    }

    public StringKey(String s, long id) {
        this.s = s;
        this.id = id;
        this.hash = s == null ? 0 : s.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof StringKey) {
            StringKey sk = (StringKey) o;
            if (id != 0 && sk.id != 0) {
                return id == sk.id;
            }
            return Objects.equals(s, sk.s);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public int compareTo(StringKey o) {
        return s.compareTo(o.s);
    }

    @Override
    public String toString() {
        return s == null ? "null" : s;
    }
}
