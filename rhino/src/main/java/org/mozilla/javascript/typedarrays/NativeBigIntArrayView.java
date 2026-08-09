package org.mozilla.javascript.typedarrays;

import java.io.Serial;
import java.math.BigInteger;
import org.mozilla.javascript.ScriptRuntime;

public abstract class NativeBigIntArrayView extends NativeTypedArrayView<BigInteger> {
    @Serial private static final long serialVersionUID = -3349222145964894609L;

    protected NativeBigIntArrayView() {
        super(Long.TYPE);
    }

    protected NativeBigIntArrayView(NativeArrayBuffer ab, int off, int len, int byteLen) {
        super(Long.TYPE, ab, off, len, byteLen);
    }

    @Override
    protected Object toNumeric(Object num) {
        return ScriptRuntime.toBigInt(num);
    }
}
