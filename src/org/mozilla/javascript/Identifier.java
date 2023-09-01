package org.mozilla.javascript;

/**
 * An identifier represents a permanent string identifier that is kept in a global pool. Identifiers
 * are compatible with String so that they may be interchangeable in a hash table.
 */
public class Identifier implements Comparable<Identifier> {
    private final long id;
    private final int hash;
    private final String name;

    public Identifier(String name, long id) {
        this.name = name;
        this.hash = name.hashCode();
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Identifier) {
            return ((Identifier) o).id == id;
        }
        if (o instanceof StringKey) {
            return o.toString().equals(name);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public int compareTo(Identifier o) {
        if (id < o.id) {
            return -1;
        }
        if (id > o.id) {
            return 1;
        }
        return 0;
    }

    @Override
    public String toString() {
        return name;
    }
}
