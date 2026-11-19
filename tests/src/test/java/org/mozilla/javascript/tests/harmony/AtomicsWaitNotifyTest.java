package org.mozilla.javascript.tests.harmony;

import org.junit.jupiter.api.Disabled;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.drivers.LanguageVersion;
import org.mozilla.javascript.drivers.RhinoTest;
import org.mozilla.javascript.drivers.ScriptTestsBase;

@RhinoTest("testsrc/jstests/harmony/atomics-wait-notify.js")
@LanguageVersion(Context.VERSION_ES6)
@Disabled
public class AtomicsWaitNotifyTest extends ScriptTestsBase {}
