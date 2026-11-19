// Tests for Atomics.wait and Atomics.notify (single-threaded subset).
// Multi-threaded scenarios (OK result, multiple waiters, count limits) are
// covered by the Java unit tests in WaitersTest.

load('testsrc/assert.js');

(function TestWaitBasic() {
  var sab = new SharedArrayBuffer(16);
  var a = new Int32Array(sab);

  // Value matches, timeout 0 => timed-out (no one to notify in single thread)
  a[0] = 5;
  assertEquals('timed-out', Atomics.wait(a, 0, 5, 0));

  // Value does not match => not-equal (returns immediately, no timeout wait)
  a[0] = 5;
  assertEquals('not-equal', Atomics.wait(a, 0, 99, 0));
})();

(function TestWaitTimeout() {
  var sab = new SharedArrayBuffer(8);
  var a = new Int32Array(sab);
  a[0] = 1;

  // Positive timeout with matching value should wait, then time out
  var start = Date.now();
  assertEquals('timed-out', Atomics.wait(a, 0, 1, 50));
  var elapsed = Date.now() - start;
  assertTrue(elapsed >= 40, 'wait should block ~50ms, got ' + elapsed + 'ms');

  // NaN timeout => treated as Infinity => very long wait, but value not equal
  // so should return immediately
  assertEquals('not-equal', Atomics.wait(a, 0, 2, NaN));

  // Infinity timeout => value not equal => immediate
  assertEquals('not-equal', Atomics.wait(a, 0, 2, Infinity));
})();

(function TestNotifyNoWaiters() {
  var sab = new SharedArrayBuffer(16);
  var a = new Int32Array(sab);

  // No one waiting => returns 0
  assertEquals(0, Atomics.notify(a, 0, 1));
  assertEquals(0, Atomics.notify(a, 0, 100));
  assertEquals(0, Atomics.notify(a, 1, 1));

  // Notify with count 0 returns 0
  assertEquals(0, Atomics.notify(a, 0, 0));
})();

(function TestWaitTypeErrorNonShared() {
  var ab = new ArrayBuffer(16);
  var a = new Int32Array(ab);

  assertThrows(function() { Atomics.wait(a, 0, 0, 0); }, TypeError);
})();

(function TestWaitTypeErrorNonInteger() {
  var sab = new SharedArrayBuffer(16);
  var fa = new Float32Array(sab);
  var f64a = new Float64Array(sab);
  var u8ca = new Uint8ClampedArray(sab);

  assertThrows(function() { Atomics.wait(fa, 0, 0, 0); }, TypeError);
  assertThrows(function() { Atomics.wait(f64a, 0, 0, 0); }, TypeError);
  assertThrows(function() { Atomics.wait(u8ca, 0, 0, 0); }, TypeError);
})();

(function TestNotifyNonShared() {
  var ab = new ArrayBuffer(16);
  var a = new Int32Array(ab);

  // Non-shared buffers cannot be waited on; notify simply returns 0
  assertEquals(0, Atomics.notify(a, 0, 1));
})();

(function TestNotifyTypeErrorNonInteger() {
  var sab = new SharedArrayBuffer(16);
  assertThrows(function() { Atomics.notify(new Float32Array(sab), 0, 1); }, TypeError);
})();

(function TestWaitBigInt64() {
  // Only Int32Array and BigInt64Array support wait/notify
  var sab = new SharedArrayBuffer(16);
  var u32a = new Uint32Array(sab);
  var a64 = new BigInt64Array(sab);
  var au64 = new BigUint64Array(sab);

  assertThrows(function() { Atomics.wait(u32a, 0, 42, 0); }, TypeError);
  assertThrows(function() { Atomics.notify(u32a, 0, 1); }, TypeError);

  a64[0] = 42n;
  assertEquals('timed-out', Atomics.wait(a64, 0, 42n, 0));
  assertEquals('not-equal', Atomics.wait(a64, 0, 43n, 0));
  assertEquals(0, Atomics.notify(a64, 0, 1));

  assertThrows(function() { Atomics.wait(au64, 0, 42n, 0); }, TypeError);
  assertThrows(function() { Atomics.notify(au64, 0, 1); }, TypeError);
})();

(function TestWaitValueCoercion() {
  var sab = new SharedArrayBuffer(8);
  var a = new Int32Array(sab);

  // Value is coerced via ToInt32
  a[0] = 0;
  assertEquals('timed-out', Atomics.wait(a, 0, false, 0));
  assertEquals('timed-out', Atomics.wait(a, 0, null, 0));
  assertEquals('timed-out', Atomics.wait(a, 0, undefined, 0));
  assertEquals('timed-out', Atomics.wait(a, 0, NaN, 0));
  assertEquals('timed-out', Atomics.wait(a, 0, '0', 0));

  a[0] = 1;
  assertEquals('timed-out', Atomics.wait(a, 0, true, 0));
  assertEquals('timed-out', Atomics.wait(a, 0, '1', 0));
  assertEquals('timed-out', Atomics.wait(a, 0, 1.9, 0));

  // valueOf coercion
  a[0] = 5;
  assertEquals('timed-out', Atomics.wait(a, 0, {valueOf: function() { return 5; }}, 0));
})();

(function TestWaitIndexCoercion() {
  var sab = new SharedArrayBuffer(32);
  var a = new Int32Array(sab);

  // Index is coerced via ToIndex
  a[0] = 10;
  assertEquals('timed-out', Atomics.wait(a, -0, 10, 0));
  assertEquals('timed-out', Atomics.wait(a, null, 10, 0));
  assertEquals('timed-out', Atomics.wait(a, undefined, 10, 0));
  assertEquals('timed-out', Atomics.wait(a, false, 10, 0));
  assertEquals('timed-out', Atomics.wait(a, '0', 10, 0));

  a[3] = 20;
  assertEquals('timed-out', Atomics.wait(a, 3, 20, 0));
  assertEquals('timed-out', Atomics.wait(a, '3', 20, 0));
  assertEquals('timed-out', Atomics.wait(a, {valueOf: function() { return 3; }}, 20, 0));
})();

(function TestWaitReturnTypes() {
  var sab = new SharedArrayBuffer(8);
  var a = new Int32Array(sab);
  a[0] = 1;

  assertEquals('timed-out', Atomics.wait(a, 0, 1, 0));
  assertEquals('not-equal', Atomics.wait(a, 0, 2, 0));
  assertEquals('number', typeof Atomics.notify(a, 0, 1));
})();

(function TestNotifyCountCoercion() {
  var sab = new SharedArrayBuffer(8);
  var a = new Int32Array(sab);

  // count defaults to Infinity when omitted
  assertEquals(0, Atomics.notify(a, 0));
  assertEquals(0, Atomics.notify(a, 0, undefined));
  assertEquals(0, Atomics.notify(a, 0, NaN));
  assertEquals(0, Atomics.notify(a, 0, Infinity));

  // Negative count clamped to 0
  assertEquals(0, Atomics.notify(a, 0, -5));
})();

(function TestWaitAndNotifyDifferentIndices() {
  var sab = new SharedArrayBuffer(32);
  var a = new Int32Array(sab);

  a[0] = 1;
  a[1] = 2;
  a[2] = 3;

  // Wait on index 0, notify index 1 => no effect (single-threaded, still timed-out)
  assertEquals('timed-out', Atomics.wait(a, 0, 1, 0));
  assertEquals(0, Atomics.notify(a, 1, 1));
  assertEquals(0, Atomics.notify(a, 2, 1));
})();

(function TestWaitAsyncNotImplemented() {
  var sab = new SharedArrayBuffer(8);
  var a = new Int32Array(sab);

  assertThrows(function() { Atomics.waitAsync(a, 0, 0, 0); }, TypeError);
})();

(function TestWaitSmallPositiveTimeout() {
  var sab = new SharedArrayBuffer(8);
  var a = new Int32Array(sab);
  a[0] = 42;

  // timeout of 1ms should still time out in single-threaded context
  assertEquals('timed-out', Atomics.wait(a, 0, 42, 1));
})();

'success';
