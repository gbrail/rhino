'use strict';

//load('testsrc/assert.js');

function assertEquals(x, y) {
  if (x !== y) {
    throw 'Not equal: ' + x + ' (' + typeof(x) + ') != ' + y + ' (' + typeof(y) + ')';
  }
}

const NUMELEMENTS = 4;

function checkOps(a) {
  a[0] = 1;
  a[1] = 2;
  a[2] = 3;
  assertEquals(a[0], 1);
  assertEquals(a[1], 2);
  assertEquals(a[2], 3);

  assertEquals(Atomics.load(a, 1), 2);
  Atomics.store(a, 1, 10);
  assertEquals(Atomics.load(a, 1), 10);
  assertEquals(a[1], 10);

  assertEquals(Atomics.add(a, 2, 2), 3);
  assertEquals(a[2], 5);
  assertEquals(Atomics.sub(a, 2, 5), 5);
  assertEquals(a[2], 0);

  a[3] = 1;
  assertEquals(Atomics.or(a, 3, 2), 1);
  assertEquals(a[3], 3);
  assertEquals(Atomics.and(a, 3, 2), 3);
  assertEquals(a[3], 2);

  assertEquals(Atomics.compareExchange(a, 0, 1, 10), 1);
  assertEquals(a[0], 10);
  assertEquals(Atomics.compareExchange(a, 0, 1, 2), 10);
  assertEquals(a[0], 10);
}

function checkBigIntOps(a) {
  a[0] = 1n;
  a[1] = 2n;
  a[2] = 3n;
  assertEquals(a[0], 1n);
  assertEquals(a[1], 2n);
  assertEquals(a[2], 3n);

  assertEquals(Atomics.load(a, 1), 2n);
  Atomics.store(a, 1, 10n);
  assertEquals(Atomics.load(a, 1), 10n);
  assertEquals(a[1n], 10n);

  assertEquals(Atomics.add(a, 2, 2n), 3n);
  assertEquals(a[2], 5n);
  assertEquals(Atomics.sub(a, 2, 5n), 5n);
  assertEquals(a[2], 0n);

  a[3] = 1n;
  assertEquals(Atomics.or(a, 3, 2n), 1n);
  assertEquals(a[3], 3n);
  assertEquals(Atomics.and(a, 3, 2n), 3n);
  assertEquals(a[3], 2n);

  assertEquals(Atomics.compareExchange(a, 0, 1n, 10n), 1n);
  assertEquals(a[0], 10n);
  assertEquals(Atomics.compareExchange(a, 0, 1n, 2n), 10n);
  assertEquals(a[0], 10n);
}

var ab = new ArrayBuffer(NUMELEMENTS * 8);
var sab = new SharedArrayBuffer(NUMELEMENTS * 8);

checkOps(new Int8Array(ab));
checkOps(new Int8Array(sab));
checkOps(new Uint8Array(ab));
checkOps(new Uint8Array(sab));
checkOps(new Int16Array(ab));
checkOps(new Int16Array(sab));
checkOps(new Uint16Array(ab));
checkOps(new Uint16Array(sab));
checkOps(new Int32Array(ab));
checkOps(new Int32Array(sab));
checkOps(new Uint32Array(ab));
checkOps(new Uint32Array(sab));

checkBigIntOps(new BigInt64Array(ab));
checkBigIntOps(new BigInt64Array(sab));
checkBigIntOps(new BigUint64Array(ab));
checkBigIntOps(new BigUint64Array(sab));

'success';
