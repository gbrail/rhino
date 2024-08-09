package org.mozilla.javascript.optimizer;

import jdk.dynalink.Operation;

public enum RhinoOperation implements Operation {
    BIND,
    GETNOWARN,
    GETWITHTHIS,
    SETSTRICT,
    CALL_0,
    CALL_1,
    CALL_2,
    ADD,
    SUBTRACT,
    MULTIPLY,
    DIVIDE,
    REMAINDER,
    NEGATE,
    EXPONENTIATE,
    BITWISE_NOT,
    BITWISE_OR,
    BITWISE_XOR,
    BITWISE_AND,
    SIGNED_RIGHT_SHIFT,
    LEFT_SHIFT,
}
