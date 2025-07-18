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
}
