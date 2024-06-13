package org.mozilla.javascript;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Objects;

/**
 * A Slot is the base class for all properties stored in the ScriptableObject class. There are a
 * number of different types of slots. This base class represents an "ordinary" property such as a
 * primitive type or another object. Separate classes are used to represent properties that have
 * various types of getter and setter methods.
 */
public class Slot implements Serializable {
    private static final long serialVersionUID = -6090581677123995491L;

    public static final class Key implements Serializable, Comparable<Key> {
        private final Object name;
        private final int index;
        private final boolean isName;

        public Key(Object n) {
            this.name = n;
            this.index = 0;
            this.isName = true;
        }

        public Key(int index) {
            this.name = null;
            this.index = index;
            this.isName = false;
        }

        public Key(Object n, int index) {
            if (n == null) {
                this.name = null;
                this.index = index;
                this.isName = false;
            } else {
                this.name = n;
                this.index = 0;
                this.isName = true;
            }
        }

        public boolean isName() {
            return isName;
        }

        public boolean isIndex() {
            return !isName;
        }

        public Object getName() {
            return name;
        }

        public int getIndex() {
            return index;
        }

        public Object toObject() {
            return isName ? name : Integer.valueOf(index);
        }

        @Override
        public int hashCode() {
            return isName ? name.hashCode() : index;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Key)) {
                return false;
            }
            Key k = (Key) o;
            if (isName) {
                if (!k.isName) {
                    return false;
                }
                return Objects.equals(name, k.name);
            }
            if (k.isName) {
                return false;
            }
            return index == k.index;
        }

        @Override
        public int compareTo(Key k) {
            if (isName) {
                if (!k.isName) {
                    // Indexes always come before everything else
                    return 1;
                }
                if (name instanceof String) {
                    if (k.name instanceof String) {
                        return ((String) name).compareTo((String) k.name);
                    }
                    // Non-string keys come after string keys
                    return -1;
                }
            }
            if (k.isName) {
                return -1;
            }
            if (index < k.index) {
                return -1;
            }
            if (index > k.index) {
                return 1;
            }
            return 0;
        }

        @Override
        public String toString() {
            return isName ? name.toString() : String.valueOf(index);
        }
    }

    Key key;
    private short attributes;
    Object value;

    Slot(Key key, int attributes) {
        this.key = key;
        this.attributes = (short) attributes;
    }

    /**
     * Return true if this is a base-class "Slot". Sadly too much code breaks if we try to do this
     * any other way.
     */
    boolean isValueSlot() {
        return true;
    }

    /**
     * Return true if this is a "setter slot" which, which we need to know for some legacy support.
     */
    boolean isSetterSlot() {
        return false;
    }

    protected Slot(Slot oldSlot) {
        key = oldSlot.key;
        attributes = oldSlot.attributes;
        value = oldSlot.value;
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
    }

    public final boolean setValue(Object value, Scriptable owner, Scriptable start) {
        return setValue(value, owner, start, Context.isCurrentContextStrict());
    }

    public boolean setValue(Object value, Scriptable owner, Scriptable start, boolean isThrow) {
        if ((attributes & ScriptableObject.READONLY) != 0) {
            if (isThrow) {
                throw ScriptRuntime.typeErrorById("msg.modify.readonly", key);
            }
            return true;
        }
        if (owner == start) {
            this.value = value;
            return true;
        }
        return false;
    }

    public Object getValue(Scriptable start) {
        return value;
    }

    int getAttributes() {
        return attributes;
    }

    synchronized void setAttributes(int value) {
        ScriptableObject.checkValidAttributes(value);
        attributes = (short) value;
    }

    ScriptableObject getPropertyDescriptor(Context cx, Scriptable scope) {
        return ScriptableObject.buildDataDescriptor(scope, value, attributes);
    }

    protected void throwNoSetterException(Scriptable start, Object newValue) {
        Context cx = Context.getContext();
        if (cx.isStrictMode()
                ||
                // Based on TC39 ES3.1 Draft of 9-Feb-2009, 8.12.4, step 2,
                // we should throw a TypeError in this case.
                cx.hasFeature(Context.FEATURE_STRICT_MODE)) {

            String prop = "";
            if (key != null) {
                prop = "[" + start.getClassName() + "]." + key;
            }
            throw ScriptRuntime.typeErrorById(
                    "msg.set.prop.no.setter", prop, Context.toString(newValue));
        }
    }

    /**
     * Return a JavaScript function that represents the "setter". This is used by some legacy
     * functionality. Return null if there is no setter.
     */
    Function getSetterFunction(String name, Scriptable scope) {
        return null;
    }

    /** Same for the "getter." */
    Function getGetterFunction(String name, Scriptable scope) {
        return null;
    }
}
