package org.mozilla.javascript;

class FastPropertyOperations {
    static ScriptableObject.FastKey getFastKey(ScriptableObject target, Object property) {
        if (target.getPrototype() instanceof ScriptableObject) {
            var proto = (ScriptableObject) target.getPrototype();
            var objKey = target.getMap().getFastQueryKey(property);
            if (objKey != null) {
                // Property is on the target object
                var protoKey = proto.getMap().getFastWildcardKey();
                if (protoKey != null) {
                    return new GetDirectObjectProp(objKey, protoKey);
                }
            } else {
                var protoKey = proto.getMap().getFastQueryKey(property);
                if (protoKey != null) {
                    // Property is on the prototype
                    objKey = target.getMap().getFastWildcardKey();
                    if (objKey != null) {
                        return new GetPrototypeObjectProp(objKey, protoKey);
                    }
                }
            }
        }
        return null;
    }

    static ScriptableObject.FastKey getFastWriteKey(
            ScriptableObject target, String property, int attributes) {
        if (target.isSealed()) {
            return null;
        }
        if (target.getPrototype() instanceof ScriptableObject) {
            var proto = (ScriptableObject) target.getPrototype();
            var objKey =
                    target.getMap().getFastModifyKey(property, attributes, target.isExtensible());
            if (objKey != null) {
                // Property can be set on the target object
                var protoKey = proto.getMap().getFastWildcardKey();
                if (protoKey != null) {
                    return new SetDirectObjectProp(objKey, protoKey);
                }
                /*} else {
                   var protoKey = proto.getMap().getFastQueryKey(property);
                   if (protoKey != null) {
                       // Property is on the prototype
                       objKey = target.getMap().getFastWildcardKey();
                       if (objKey != null) {
                           return new GetPrototypeObjectProp(objKey, protoKey);
                       }
                   }
                */
            }
        }
        return null;
    }

    abstract static class GetObjectProp implements ScriptableObject.FastKey {
        protected final SlotMap.Key objKey;
        protected final SlotMap.Key protoKey;

        protected GetObjectProp(SlotMap.Key objKey, SlotMap.Key protoKey) {
            assert objKey != null;
            assert protoKey != null;
            this.objKey = objKey;
            this.protoKey = protoKey;
        }

        @Override
        public boolean isCompatible(ScriptableObject target) {
            // Fast operation is only compatible if both target and prototype
            // have not changed shape.
            if (target.getPrototype() instanceof ScriptableObject) {
                ScriptableObject proto = (ScriptableObject) target.getPrototype();
                return objKey.isCompatible(target.getMap())
                        && protoKey.isCompatible(proto.getMap());
            }
            return false;
        }

        abstract Object getProperty(ScriptableObject target, Scriptable start);
    }

    static final class GetDirectObjectProp extends GetObjectProp {
        private GetDirectObjectProp(SlotMap.Key objKey, SlotMap.Key protoKey) {
            super(objKey, protoKey);
        }

        @Override
        Object getProperty(ScriptableObject target, Scriptable start) {
            Slot slot = target.getMap().queryFast(objKey);
            // Key should already have been validated
            assert slot != null;
            return slot.getValue(start);
        }
    }

    static final class GetPrototypeObjectProp extends GetObjectProp {
        private GetPrototypeObjectProp(SlotMap.Key objKey, SlotMap.Key protoKey) {
            super(objKey, protoKey);
        }

        @Override
        Object getProperty(ScriptableObject target, Scriptable start) {
            ScriptableObject proto = (ScriptableObject) target.getPrototype();
            Slot slot = proto.getMap().queryFast(protoKey);
            // Key should already have been validated
            assert slot != null;
            return slot.getValue(start);
        }
    }

    abstract static class SetObjectProp implements ScriptableObject.FastKey {
        protected final SlotMap.Key objKey;
        protected final SlotMap.Key protoKey;

        protected SetObjectProp(SlotMap.Key objKey, SlotMap.Key protoKey) {
            assert objKey != null;
            assert protoKey != null;
            this.objKey = objKey;
            this.protoKey = protoKey;
        }

        @Override
        public boolean isCompatible(ScriptableObject target) {
            if (target.isSealed()) {
                // Let non-optimized code deal with sealed objects
                return false;
            }
            if (target.getPrototype() instanceof ScriptableObject) {
                ScriptableObject proto = (ScriptableObject) target.getPrototype();
                return objKey.isCompatible(target.getMap())
                        && protoKey.isCompatible(proto.getMap());
            }
            return false;
        }

        abstract boolean putProperty(
                ScriptableObject target, Scriptable start, Object value, boolean isThrow);
    }

    static final class SetDirectObjectProp extends SetObjectProp {
        SetDirectObjectProp(SlotMap.Key objKey, SlotMap.Key protoKey) {
            super(objKey, protoKey);
        }

        @Override
        public boolean isCompatible(ScriptableObject target) {
            if (objKey.isExtending() && !target.isExtensible()) {
                // Let non-optimized code deal with extending non-extensible objects
                return false;
            }
            return super.isCompatible(target);
        }

        @Override
        boolean putProperty(
                ScriptableObject target, Scriptable start, Object value, boolean isThrow) {
            Slot slot = target.getMap().modifyFast(objKey);
            assert slot != null;
            return slot.setValue(value, target, start, isThrow);
        }
    }
}
