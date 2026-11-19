package org.mozilla.javascript.tests.harmony;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.drivers.LanguageVersion;
import org.mozilla.javascript.drivers.RhinoTest;
import org.mozilla.javascript.drivers.ScriptTestsBase;

@RhinoTest("testsrc/jstests/harmony/checkallatomics.js")
@LanguageVersion(Context.VERSION_ES6)
public class AllAtomicOpsTest extends ScriptTestsBase {}
