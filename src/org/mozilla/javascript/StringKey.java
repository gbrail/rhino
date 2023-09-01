package org.mozilla.javascript;

/** A StringKey holds a property name in such a way that it is comparable with an identifier. */
public class StringKey implements Comparable<StringKey> {
    private final String s;
    private final int hash;

    public StringKey(String s) {
        this.s = s;
        this.hash = s.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof StringKey) {
            return ((StringKey) o).s.equals(s);
        }
        if (o instanceof Identifier) {
            return o.toString().equals(s);
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
        return s;
    }
}
