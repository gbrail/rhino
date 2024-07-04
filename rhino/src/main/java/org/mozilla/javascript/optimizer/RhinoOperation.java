package org.mozilla.javascript.optimizer;

import jdk.dynalink.Operation;

public enum RhinoOperation implements Operation {
    GETNOWARN,
    SETSTRICT,
    BIND,
    PREINCREMENT,
    POSTINCREMENT,
    PREDECREMENT,
    POSTDECREMENT,
}
