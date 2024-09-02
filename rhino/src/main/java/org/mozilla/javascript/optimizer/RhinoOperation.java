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
}
