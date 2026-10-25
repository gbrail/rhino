// Port of WPT structured-clone-battery-of-tests.js and
// structured-clone-battery-of-tests-with-transferables.js
// https://github.com/web-platform-tests/wpt/tree/master/html/webappapis/structured-clone
// Adapted for Rhino: synchronous, no browser-specific types, uses assert.js harness.

load("testsrc/assert.js");

(function TestPrimitives() {
  var copy;

  copy = structuredClone(undefined);
  assertEquals(undefined, copy, "primitive undefined");

  copy = structuredClone(null);
  assertEquals(null, copy, "primitive null");

  copy = structuredClone(true);
  assertEquals(true, copy, "primitive true");

  copy = structuredClone(false);
  assertEquals(false, copy, "primitive false");

  copy = structuredClone("");
  assertEquals("", copy, "primitive string empty");

  copy = structuredClone("\uD800");
  assertEquals("\uD800", copy, "primitive string lone high surrogate");

  copy = structuredClone("\uDC00");
  assertEquals("\uDC00", copy, "primitive string lone low surrogate");

  copy = structuredClone("\u0000");
  assertEquals("\u0000", copy, "primitive string NUL");

  copy = structuredClone("\uDBFF\uDFFD");
  assertEquals("\uDBFF\uDFFD", copy, "primitive string astral character");

  copy = structuredClone(0.2);
  assertEquals(0.2, copy, "primitive number 0.2");

  copy = structuredClone(0);
  assertEquals(0, copy, "primitive number 0");

  copy = structuredClone(-0);
  assertTrue((1 / copy) < 0, "primitive number -0");

  copy = structuredClone(NaN);
  assertTrue(isNaN(copy), "primitive number NaN");

  copy = structuredClone(Infinity);
  assertEquals(Infinity, copy, "primitive number Infinity");

  copy = structuredClone(-Infinity);
  assertEquals(-Infinity, copy, "primitive number -Infinity");

  copy = structuredClone(9007199254740992);
  assertEquals(9007199254740992, copy, "primitive number large");

  copy = structuredClone(-9007199254740992);
  assertEquals(-9007199254740992, copy, "primitive number negative large");

  copy = structuredClone(9007199254740994);
  assertEquals(9007199254740994, copy, "primitive number larger");

  copy = structuredClone(-9007199254740994);
  assertEquals(-9007199254740994, copy, "primitive number negative larger");

  copy = structuredClone(0n);
  assertEquals(0n, copy, "primitive BigInt 0n");

  copy = structuredClone(-0n);
  assertEquals(-0n, copy, "primitive BigInt -0n");

  copy = structuredClone(-9007199254740994000n);
  assertEquals(-9007199254740994000n, copy, "primitive BigInt large negative");

  copy = structuredClone(
    -9007199254740994000900719925474099400090071992547409940009007199254740994000n
  );
  assertEquals(
    -9007199254740994000900719925474099400090071992547409940009007199254740994000n,
    copy,
    "primitive BigInt very large negative"
  );
})();

(function TestPrimitivesInArray() {
  var input = [
    undefined, null, true, false, "", "\uD800", "\uDC00", "\u0000",
    "\uDBFF\uDFFD", 0.2, 0, -0, NaN, Infinity, -Infinity,
    9007199254740992, -9007199254740992, 9007199254740994,
    -9007199254740994, -12n, -0n, 0n
  ];
  var copy = structuredClone(input);
  assertTrue(copy instanceof Array, "Array primitives instanceof Array");
  assertTrue(copy !== input, "Array primitives not same reference");
  assertEquals(input.length, copy.length, "Array primitives length");
  for (var i = 0; i < input.length; i++) {
    if (typeof input[i] === "number" && isNaN(input[i])) {
      assertTrue(isNaN(copy[i]), "Array primitives element " + i + " is NaN");
    } else if (Object.is(input[i], -0)) {
      assertTrue((1 / copy[i]) < 0, "Array primitives element " + i + " is -0");
    } else {
      assertEquals(input[i], copy[i], "Array primitives element " + i);
    }
  }
})();

(function TestPrimitivesInObject() {
  var input = {
    "undefined": undefined,
    "null": null,
    "true": true,
    "false": false,
    "empty": "",
    "highSurrogate": "\uD800",
    "lowSurrogate": "\uDC00",
    "nul": "\u0000",
    "astral": "\uDBFF\uDFFD",
    "pointTwo": 0.2,
    "zero": 0,
    "negZero": -0,
    "nan": NaN,
    "inf": Infinity,
    "negInf": -Infinity,
    "largePos": 9007199254740992,
    "largeNeg": -9007199254740992,
    "largerPos": 9007199254740994,
    "largerNeg": -9007199254740994
  };
  var copy = structuredClone(input);
  assertTrue(copy instanceof Object, "Object primitives instanceof Object");
  assertFalse(copy instanceof Array, "Object primitives not instanceof Array");
  assertTrue(copy !== input, "Object primitives not same reference");
  for (var key in input) {
    if (typeof input[key] === "number" && isNaN(input[key])) {
      assertTrue(isNaN(copy[key]), "Object primitives property " + key + " is NaN");
    } else if (Object.is(input[key], -0)) {
      assertTrue((1 / copy[key]) < 0, "Object primitives property " + key + " is -0");
    } else {
      assertEquals(input[key], copy[key], "Object primitives property " + key);
    }
  }
})();

(function TestBooleanObjects() {
  var copy;

  copy = structuredClone(new Boolean(true));
  assertInstanceof(copy, Boolean);
  assertEquals(String(copy), String(true), "Boolean true converted to primitive");
  assertTrue(copy !== true, "Boolean true not same as primitive");

  copy = structuredClone(new Boolean(false));
  assertInstanceof(copy, Boolean);
  assertEquals(String(copy), String(false), "Boolean false converted to primitive");
  assertTrue(copy !== false, "Boolean false not same as primitive");
})();

(function TestBooleanObjectsInContainer() {
  var arrInput = [new Boolean(true), new Boolean(false)];
  var arrCopy = structuredClone(arrInput);
  assertTrue(arrCopy instanceof Array, "Array Boolean objects instanceof Array");
  assertEquals(arrInput.length, arrCopy.length, "Array Boolean objects length");
  assertInstanceof(arrCopy[0], Boolean);
  assertInstanceof(arrCopy[1], Boolean);
  assertEquals(String(arrCopy[0]), String(true), "Array Boolean objects element 0");
  assertEquals(String(arrCopy[1]), String(false), "Array Boolean objects element 1");

  var objInput = {"true": new Boolean(true), "false": new Boolean(false)};
  var objCopy = structuredClone(objInput);
  assertTrue(objCopy instanceof Object, "Object Boolean objects instanceof Object");
  assertFalse(objCopy instanceof Array, "Object Boolean objects not instanceof Array");
  assertInstanceof(objCopy["true"], Boolean);
  assertInstanceof(objCopy["false"], Boolean);
})();

(function TestStringObjects() {
  var strValues = ["", "\uD800", "\uDC00", "\u0000", "\uDBFF\uDFFD"];
  for (var i = 0; i < strValues.length; i++) {
    var copy = structuredClone(new String(strValues[i]));
    assertInstanceof(copy, String);
    assertEquals(String(copy), String(strValues[i]), "String object value matches");
    assertTrue(copy !== strValues[i], "String object not same as primitive");
  }
})();

(function TestStringObjectsInContainer() {
  var strValues = ["", "\uD800", "\uDC00", "\u0000", "\uDBFF\uDFFD"];
  var arrInput = [];
  for (var i = 0; i < strValues.length; i++) {
    arrInput.push(new String(strValues[i]));
  }
  var arrCopy = structuredClone(arrInput);
  assertTrue(arrCopy instanceof Array, "Array String objects instanceof Array");
  assertEquals(arrInput.length, arrCopy.length, "Array String objects length");
  for (i = 0; i < arrCopy.length; i++) {
    assertInstanceof(arrCopy[i], String);
    assertEquals(String(arrCopy[i]), String(strValues[i]), "Array String objects element " + i);
  }
})();

(function TestNumberObjects() {
  var numValues = [0.2, 0, -0, NaN, Infinity, -Infinity, 9007199254740992, -9007199254740992, 9007199254740994, -9007199254740994];
  for (var i = 0; i < numValues.length; i++) {
    var copy = structuredClone(new Number(numValues[i]));
    assertInstanceof(copy, Number);
    if (isNaN(numValues[i])) {
      assertTrue(isNaN(Number(copy)), "Number object NaN value matches");
    } else if (numValues[i] === 0) {
      assertTrue(Number(copy) === 0, "Number object 0/-0 value matches");
      if ((1 / numValues[i]) < 0) {
        assertTrue((1 / Number(copy)) < 0, "Number object -0 preserved");
      }
    } else {
      assertEquals(Number(copy), numValues[i], "Number object value matches index " + i);
    }
    assertTrue(copy !== numValues[i], "Number object not same as primitive");
  }
})();

(function TestBigIntObjects() {
  var copy = structuredClone(Object(-9007199254740994n));
  assertInstanceof(copy, BigInt);
  assertEquals(BigInt(copy), BigInt(-9007199254740994n), "BigInt object value matches");
})();

(function TestDateObjects() {
  var dates = [new Date(0), new Date(-0), new Date(-8640000000000), new Date(8640000000000)];
  for (var i = 0; i < dates.length; i++) {
    var copy = structuredClone(dates[i]);
    assertInstanceof(copy, Date);
    assertEquals(Number(copy), Number(dates[i]), "Date object timestamp matches index " + i);
    assertTrue(copy !== dates[i], "Date object not same reference");
  }

  // Invalid date (NaN timestamp)
  var invalidDate = new Date("invalid");
  var invalidCopy = structuredClone(invalidDate);
  assertInstanceof(invalidCopy, Date);
  assertTrue(isNaN(Number(invalidCopy)), "Invalid Date preserves NaN timestamp");
})();

(function TestRegExpObjects() {
  var r = /foo/gim;
  r.lastIndex = 2;
  var copy = structuredClone(r);
  assertInstanceof(copy, RegExp);
  assertEquals(copy.global, true, "RegExp global flag");
  assertEquals(copy.ignoreCase, true, "RegExp ignoreCase flag");
  assertEquals(copy.multiline, true, "RegExp multiline flag");
  assertEquals(copy.source, "foo", "RegExp source");
  assertEquals(copy.lastIndex, 0, "RegExp lastIndex reset to 0");
  assertTrue(copy !== r, "RegExp not same reference");

  var stickyR = new RegExp("foo", "y");
  var stickyCopy = structuredClone(stickyR);
  assertInstanceof(stickyCopy, RegExp);
  assertEquals(stickyCopy.sticky, true, "RegExp sticky flag");
  assertEquals(stickyCopy.source, "foo", "RegExp sticky source");

  var unicodeR = new RegExp("foo", "u");
  var unicodeCopy = structuredClone(unicodeR);
  assertInstanceof(unicodeCopy, RegExp);
  assertEquals(unicodeCopy.unicode, true, "RegExp unicode flag");

  var emptyR = new RegExp("");
  var emptyCopy = structuredClone(emptyR);
  assertInstanceof(emptyCopy, RegExp);
  assertEquals("", emptyCopy.source, "RegExp empty source");

  var slashR = new RegExp("/");
  var slashCopy = structuredClone(slashR);
  assertInstanceof(slashCopy, RegExp);
  assertEquals("\\/", slashCopy.source, "RegExp slash source escaped");

  var nlR = new RegExp("\n");
  var nlCopy = structuredClone(nlR);
  assertInstanceof(nlCopy, RegExp);
  assertEquals("\n", nlCopy.source, "RegExp newline source matches original");
})();

(function TestErrorObjects() {
  function compareError(actual, input, typeName) {
    assertInstanceof(actual, Error);
    assertEquals(actual.constructor, input.constructor, typeName + " constructor");
    assertEquals(actual.name, input.name, typeName + " name");
    assertEquals(actual.hasOwnProperty("message"), input.hasOwnProperty("message"), typeName + " message existence");
    assertEquals(actual.message, input.message, typeName + " message");
    assertEquals(actual.cause, input.cause, typeName + " cause");
    assertEquals(actual.foo, undefined, typeName + " custom property stripped");
  }

  var emptyErr = new Error();
  var emptyCopy = structuredClone(emptyErr);
  compareError(emptyCopy, emptyErr, "Empty Error");

  var errorConstructors = [Error, EvalError, RangeError, ReferenceError, SyntaxError, TypeError, URIError];
  for (var i = 0; i < errorConstructors.length; i++) {
    var Ctor = errorConstructors[i];
    var err = new Ctor("Error message here", { cause: "my cause" });
    err.foo = "testing";
    var errCopy = structuredClone(err);
    compareError(errCopy, err, Ctor.name);
  }
})();

(function TestMapCloning() {
  var map = new Map();
  map.set("a", 1);
  map.set("b", [2, 3]);
  map.set("c", { d: 4 });
  var copy = structuredClone(map);
  assertInstanceof(copy, Map);
  assertEquals(copy.size, 3, "Map size");
  assertEquals(copy.get("a"), 1, "Map string key");
  assertEquals(copy.get("b").length, 2, "Map array value");
  assertEquals(copy.get("c").d, 4, "Map object value");
  assertTrue(copy.get("b") !== map.get("b"), "Map array value deep cloned");
  assertTrue(copy.get("c") !== map.get("c"), "Map object value deep cloned");

  // Map with various key types
  var map2 = new Map();
  map2.set(1, "number key");
  map2.set(true, "boolean key");
  map2.set("str", "string key");
  var copy2 = structuredClone(map2);
  assertInstanceof(copy2, Map);
  assertEquals(copy2.get(1), "number key", "Map number key");
  assertEquals(copy2.get(true), "boolean key", "Map boolean key");
  assertEquals(copy2.get("str"), "string key", "Map string key");
})();

(function TestSetCloning() {
  var set = new Set([1, "two", { three: 3 }]);
  var copy = structuredClone(set);
  assertInstanceof(copy, Set);
  assertEquals(copy.size, 3, "Set size");
  assertTrue(copy.has(1), "Set has number");
  assertTrue(copy.has("two"), "Set has string");

  // Check object was deep cloned but equivalent
  var foundObj = false;
  copy.forEach(function(v) {
    if (typeof v === "object" && v.three === 3) {
      foundObj = true;
    }
  });
  assertTrue(foundObj, "Set has cloned object");
})();

(function TestTypedArrays() {
  var ta = new Uint8Array([1, 2, 3, 4, 5]);
  var copy = structuredClone(ta);
  assertInstanceof(copy, Uint8Array);
  assertEquals(copy.length, 5, "Uint8Array length");
  assertEquals(copy.byteLength, 5, "Uint8Array byteLength");
  assertEquals(copy.byteOffset, 0, "Uint8Array byteOffset");
  assertTrue(copy.buffer !== ta.buffer, "Uint8Array buffer different");
  for (var i = 0; i < 5; i++) {
    assertEquals(copy[i], ta[i], "Uint8Array element " + i);
  }

  var int16 = new Int16Array([100, -200, 300]);
  var int16Copy = structuredClone(int16);
  assertInstanceof(int16Copy, Int16Array);
  assertEquals(int16Copy.length, 3, "Int16Array length");
  for (i = 0; i < 3; i++) {
    assertEquals(int16Copy[i], int16[i], "Int16Array element " + i);
  }

  var float32 = new Float32Array([1.5, -2.5, 0.0]);
  var float32Copy = structuredClone(float32);
  assertInstanceof(float32Copy, Float32Array);
  assertEquals(float32Copy.length, 3, "Float32Array length");
  for (i = 0; i < 3; i++) {
    assertEquals(float32Copy[i], float32[i], "Float32Array element " + i);
  }
})();

(function TestDataView() {
  var buf = new ArrayBuffer(8);
  var view = new DataView(buf);
  view.setInt32(0, 0x12345678);
  view.setFloat32(4, 3.14);
  var copy = structuredClone(view);
  assertInstanceof(copy, DataView);
  assertEquals(copy.byteLength, 8, "DataView byteLength");
  assertEquals(copy.byteOffset, 0, "DataView byteOffset");
  assertTrue(copy.buffer !== view.buffer, "DataView buffer different");
  assertEquals(copy.getInt32(0), 0x12345678, "DataView getInt32");
  assertEqualsDelta(3.14, copy.getFloat32(4), 0.001, "DataView getFloat32");
})();

(function TestArrayBufferClone() {
  var buf = new ArrayBuffer(16);
  var view = new Uint8Array(buf);
  view[0] = 42;
  view[15] = 255;
  var copy = structuredClone(buf);
  assertInstanceof(copy, ArrayBuffer);
  assertEquals(copy.byteLength, 16, "ArrayBuffer byteLength");
  var copyView = new Uint8Array(copy);
  assertEquals(copyView[0], 42, "ArrayBuffer data preserved at 0");
  assertEquals(copyView[15], 255, "ArrayBuffer data preserved at 15");
  assertTrue(copy !== buf, "ArrayBuffer not same reference");
})();

(function TestDetachedArrayBufferThrows() {
  var buf = new ArrayBuffer(8);
  // Transfer to detach
  structuredClone(buf, { transfer: [buf] });
  // Now buf is detached; cloning it should throw
  assertThrows(function() {
    structuredClone(buf);
  }, DataCloneError);
})();

(function TestSparseArray() {
  var sparse = new Array(10);
  sparse[3] = "three";
  sparse[7] = "seven";
  var copy = structuredClone(sparse);
  assertTrue(copy instanceof Array, "Sparse array instanceof Array");
  assertEquals(copy.length, 10, "Sparse array length");
  assertEquals(copy[3], "three", "Sparse array element 3");
  assertEquals(copy[7], "seven", "Sparse array element 7");
  assertFalse(0 in copy, "Sparse array hole at 0");
  assertFalse(1 in copy, "Sparse array hole at 1");
})();

(function TestArrayNonIndexProperty() {
  var arr = [];
  arr.foo = "bar";
  arr[0] = "first";
  var copy = structuredClone(arr);
  assertTrue(copy instanceof Array, "Array with non-index prop instanceof Array");
  assertEquals(copy.foo, "bar", "Array non-index property preserved");
  assertEquals(copy[0], "first", "Array indexed element preserved");
})();

(function TestObjectWithNumericPropertyAndLength() {
  var obj = {"0": "foo", "length": 1};
  var copy = structuredClone(obj);
  assertTrue(copy instanceof Object, "Object with numeric props instanceof Object");
  assertFalse(copy instanceof Array, "Object with numeric props not instanceof Array");
  assertEquals("foo", copy["0"], "Object numeric property");
  assertEquals(copy.length, 1, "Object length property");
})();

(function TestCircularReference() {
  var arr = [];
  arr[0] = arr;
  var copy = structuredClone(arr);
  assertTrue(copy[0] === copy, "Array circular reference preserved");

  var obj = {};
  obj.x = obj;
  var objCopy = structuredClone(obj);
  assertTrue(objCopy.x === objCopy, "Object circular reference preserved");
})();

(function TestIdenticalPropertyValues() {
  var sharedObj = {};
  var arr = [sharedObj, sharedObj];
  var arrCopy = structuredClone(arr);
  assertTrue(arrCopy[0] === arrCopy[1], "Array identical property values same reference");

  var obj = {x: sharedObj, y: sharedObj};
  var objCopy = structuredClone(obj);
  assertTrue(objCopy.x === objCopy.y, "Object identical property values same reference");
})();

(function TestPrototypePropertyExcluded() {
  var Foo = function() {};
  Foo.prototype = {"foo": "bar"};
  var inst = new Foo();
  var copy = structuredClone(inst);
  assertTrue(copy instanceof Object, "Object clone instanceof Object");
  assertFalse("foo" in copy, "Prototype property excluded from clone");
})();

(function TestNonEnumerablePropertyExcluded() {
  var rv = {};
  Object.defineProperty(rv, "foo", {
    value: "bar",
    enumerable: false,
    writable: true,
    configurable: true
  });
  rv.normal = "value";
  var copy = structuredClone(rv);
  assertFalse("foo" in copy, "Non-enumerable property excluded");
  assertEquals(copy.normal, "value", "Enumerable property included");
})();

(function TestWritableProperty() {
  var rv = {};
  Object.defineProperty(rv, "foo", {
    value: "bar",
    enumerable: true,
    writable: false,
    configurable: true
  });
  var copy = structuredClone(rv);
  assertEquals(copy.foo, "bar", "Non-writable property value preserved");
  copy.foo += " baz";
  assertEquals(copy.foo, "bar baz", "Non-writable property is writable in clone");
})();

(function TestConfigurableProperty() {
  var rv = {};
  Object.defineProperty(rv, "foo", {
    value: "bar",
    enumerable: true,
    writable: true,
    configurable: false
  });
  var copy = structuredClone(rv);
  assertEquals(copy.foo, "bar", "Non-configurable property value preserved");
  delete copy.foo;
  assertFalse("foo" in copy, "Non-configurable property is deletable in clone");
})();

(function TestGetterThatThrows() {
  var exception = new Error("getter threw");
  var testObj = {
    get testProperty() {
      throw exception;
    }
  };
  assertThrows(function() {
    structuredClone(testObj);
  }, Error);
})();

(function TestObjectPrototypeLosesExoticness() {
  var copy = structuredClone(Object.prototype);
  assertTrue(copy !== Object.prototype, "Cloned Object.prototype not same reference");
  assertTrue(copy instanceof Object, "Cloned Object.prototype instanceof Object");

  var newProto = {some: "proto"};
  // Must not throw:
  Object.setPrototypeOf(copy, newProto);
  assertEquals(Object.getPrototypeOf(copy), newProto, "Cloned Object.prototype prototype changed");
})();

(function TestTransferArrayBuffer() {
  var buf = new ArrayBuffer(16);
  var origView = new Uint8Array(buf);
  origView[0] = 42;
  var copy = structuredClone(buf, {transfer: [buf]});
  assertEquals(buf.byteLength, 0, "Source ArrayBuffer detached after transfer");
  assertEquals(copy.byteLength, 16, "Transferred ArrayBuffer byteLength");
  var copyView = new Uint8Array(copy);
  assertEquals(copyView[0], 42, "Transferred ArrayBuffer data preserved");
})();

(function TestTransferDetachedArrayBufferThrows() {
  var buf = new ArrayBuffer(8);
  structuredClone(buf, {transfer: [buf]});
  // buf is now detached
  assertThrows(function() {
    structuredClone(buf, {transfer: [buf]});
  }, DataCloneError);
})();

(function TestTransferResizableArrayBuffer() {
  var buf = new ArrayBuffer(16, {maxByteLength: 1024});
  assertTrue(buf.resizable, "Source buffer is resizable");
  var copy = structuredClone(buf, {transfer: [buf]});
  assertEquals(buf.byteLength, 0, "Source resizable buffer detached");
  assertEquals(copy.byteLength, 16, "Transferred resizable buffer byteLength");
  assertEquals(copy.maxByteLength, 1024, "Transferred resizable buffer maxByteLength");
  assertTrue(copy.resizable, "Transferred buffer is resizable");
})();

(function TestTransferLengthTrackingTypedArray() {
  var ab = new ArrayBuffer(16, {maxByteLength: 1024});
  assertTrue(ab.resizable, "Source buffer is resizable");
  var ta = new Uint8Array(ab);
  var copy = structuredClone(ta, {transfer: [ab]});
  assertEquals(ab.byteLength, 0, "Source buffer detached");
  assertEquals(copy.buffer.byteLength, 16, "Transferred TypedArray buffer byteLength");
  assertEquals(copy.buffer.maxByteLength, 1024, "Transferred TypedArray buffer maxByteLength");
  assertTrue(copy.buffer.resizable, "Transferred TypedArray buffer is resizable");
  copy.buffer.resize(32);
  assertEquals(32, copy.byteLength, "Transferred TypedArray tracks resize");
})();

(function TestTransferLengthTrackingDataView() {
  var ab = new ArrayBuffer(16, {maxByteLength: 1024});
  assertTrue(ab.resizable, "Source buffer is resizable");
  var dv = new DataView(ab);
  var copy = structuredClone(dv, {transfer: [ab]});
  assertEquals(ab.byteLength, 0, "Source buffer detached");
  assertEquals(copy.buffer.byteLength, 16, "Transferred DataView buffer byteLength");
  assertEquals(copy.buffer.maxByteLength, 1024, "Transferred DataView buffer maxByteLength");
  assertTrue(copy.buffer.resizable, "Transferred DataView buffer is resizable");
  copy.buffer.resize(32);
  assertEquals(32, copy.byteLength, "Transferred DataView tracks resize");
})();

(function TestTransferOOBTypedArrayThrows() {
  var ab = new ArrayBuffer(16, {maxByteLength: 1024});
  var ta = new Uint8Array(ab, 8);
  ab.resize(0);
  assertThrows(function() {
    structuredClone(ta, {transfer: [ab]});
  }, DataCloneError);
})();

(function TestTransferOOBDataViewThrows() {
  var ab = new ArrayBuffer(16, {maxByteLength: 1024});
  var dv = new DataView(ab, 8);
  ab.resize(0);
  assertThrows(function() {
    structuredClone(dv, {transfer: [ab]});
  }, DataCloneError);
})();

(function TestTransferBufferListedBeforeViews() {
  var ab = new ArrayBuffer(8);
  var v1 = new Int32Array(ab);
  var v2 = new Int32Array(ab, 4);
  v1[0] = 0x11223344;
  v2[0] = 0x55667788;
  // The buffer is serialized before its views, so the views are created on the
  // buffer that was moved by the transfer
  var copy = structuredClone({ab: ab, v1: v1, v2: v2}, {transfer: [ab]});
  assertEquals(8, copy.ab.byteLength, "Transferred buffer byteLength");
  assertEquals(0x11223344, copy.v1[0], "First view data preserved");
  assertEquals(0x55667788, copy.v2[0], "Second view data preserved");
  assertTrue(copy.v1.buffer === copy.ab, "First view shares moved buffer");
  assertTrue(copy.v2.buffer === copy.ab, "Second view shares moved buffer");
  assertEquals(0, ab.byteLength, "Source buffer detached");
  assertEquals(0, v1.byteLength, "Source view zero length");
  assertEquals(0, v2.byteLength, "Second source view zero length");
})();

(function TestTransferDataViewListedAfterBuffer() {
  var ab = new ArrayBuffer(8);
  var dv = new DataView(ab, 2, 4);
  dv.setInt32(0, 0xAABBCCDD, true);
  var copy = structuredClone({ab: ab, dv: dv}, {transfer: [ab]});
  assertEquals(4, copy.dv.byteLength, "DataView byteLength preserved");
  assertEquals(2, copy.dv.byteOffset, "DataView byteOffset preserved");
  assertEquals(0xAABBCCDD, copy.dv.getUint32(0, true), "DataView data preserved");
  assertTrue(copy.dv.buffer === copy.ab, "DataView shares moved buffer");
  assertEquals(0, ab.byteLength, "Source buffer detached");
})();

(function TestViewOnPreviouslyDetachedBufferThrows() {
  var ab = new ArrayBuffer(4);
  var v = new Int32Array(ab);
  structuredClone(ab, {transfer: [ab]});
  // v now views a detached buffer; cloning it must fail with DataCloneError
  assertThrows(function() {
    structuredClone(v);
  }, DataCloneError);
})();

(function TestPlainCloneAutoLengthTypedArray() {
  var ab = new ArrayBuffer(16, {maxByteLength: 1024});
  var ta = new Uint8Array(ab);
  var copy = structuredClone(ta);
  assertTrue(copy.buffer.resizable, "Cloned buffer is resizable");
  copy.buffer.resize(32);
  assertEquals(32, copy.byteLength, "Cloned auto-length view tracks resize");
})();

(function TestPlainCloneAutoLengthDataView() {
  var ab = new ArrayBuffer(16, {maxByteLength: 1024});
  var dv = new DataView(ab);
  dv.setUint8(0, 7);
  var copy = structuredClone(dv);
  assertTrue(copy.buffer.resizable, "Cloned buffer is resizable");
  assertEquals(7, copy.getUint8(0), "DataView data preserved");
  copy.buffer.resize(32);
  assertEquals(32, copy.byteLength, "Cloned auto-length DataView tracks resize");
})();

(function TestNonCloneableFunction() {
  assertThrows(function() {
    structuredClone(function() {});
  }, DataCloneError);
})();

(function TestNonCloneableSymbol() {
  assertThrows(function() {
    structuredClone(Symbol("test"));
  }, DataCloneError);
})();

(function TestNonCloneableObjectWithSymbolKey() {
  var obj = {};
  var sym = Symbol("key");
  obj[sym] = "value";
  var copy = structuredClone(obj);
  // Symbol-keyed properties should be excluded
  assertEquals(copy[sym], undefined, "Symbol-keyed property excluded");
})();

(function TestNestedStructures() {
  var nested = {
    a: [1, {b: [2, {c: 3}]}],
    d: {e: new Date(0)},
    f: new Map([["g", {h: 4}]])
  };
  var copy = structuredClone(nested);
  assertEquals(copy.a[0], 1, "Nested array element");
  assertEquals(copy.a[1].b[0], 2, "Deeply nested array element");
  assertEquals(copy.a[1].b[1].c, 3, "Very deeply nested object");
  assertInstanceof(copy.d.e, Date);
  assertEquals(Number(copy.d.e), 0, "Nested Date");
  assertInstanceof(copy.f, Map);
  assertEquals(copy.f.get("g").h, 4, "Nested Map value");
})();

(function TestSharedReferenceInNestedStructure() {
  var shared = {value: 42};
  var obj = {a: shared, b: shared};
  var copy = structuredClone(obj);
  assertTrue(copy.a === copy.b, "Shared reference preserved in nested structure");
  assertEquals(copy.a.value, 42, "Shared reference value correct");
})();

(function TestAllTypedArrayTypes() {
  var types = [
    ["Int8Array", Int8Array, [-128, 0, 127]],
    ["Uint8Array", Uint8Array, [0, 128, 255]],
    ["Int16Array", Int16Array, [-32768, 0, 32767]],
    ["Uint16Array", Uint16Array, [0, 32768, 65535]],
    ["Int32Array", Int32Array, [-2147483648, 0, 2147483647]],
    ["Uint32Array", Uint32Array, [0, 2147483648, 4294967295]],
    ["Float32Array", Float32Array, [0.0, 1.5, -2.5]],
    ["Float64Array", Float64Array, [0.0, Math.PI, -Math.E]],
    ["Uint8ClampedArray", Uint8ClampedArray, [0, 128, 255]]
  ];
  if (typeof BigInt64Array !== "undefined") {
    types.push(["BigInt64Array", BigInt64Array, [-9007199254740991n, 0n, 9007199254740991n]]);
  }
  if (typeof BigUint64Array !== "undefined") {
    types.push(["BigUint64Array", BigUint64Array, [0n, 9007199254740991n, 18014398509481983n]]);
  }

  for (var i = 0; i < types.length; i++) {
    var entry = types[i];
    var name = entry[0];
    var Ctor = entry[1];
    var vals = entry[2];
    var ta = new Ctor(vals);
    var copy = structuredClone(ta);
    assertInstanceof(copy, Ctor);
    assertEquals(copy.length, vals.length, name + " length");
    assertTrue(copy.buffer !== ta.buffer, name + " buffer different");
    for (var j = 0; j < vals.length; j++) {
      assertEquals(copy[j], vals[j], name + " element " + j);
    }
  }
})();

(function TestTypedArrayWithOffset() {
  var buf = new ArrayBuffer(16);
  var ta = new Uint8Array(buf, 4, 8);
  for (var i = 0; i < 8; i++) {
    ta[i] = i * 10;
  }
  var copy = structuredClone(ta);
  assertInstanceof(copy, Uint8Array);
  assertEquals(copy.length, 8, "Offset TypedArray length");
  // Note: Rhino preserves original offset for shared-buffer support
  assertEquals(4, copy.byteOffset, "Offset TypedArray byteOffset preserved");
  for (i = 0; i < 8; i++) {
    assertEquals(copy[i], i * 10, "Offset TypedArray element " + i);
  }
})();

(function TestAggregateError() {
  var inner1 = new Error("inner1");
  var inner2 = new TypeError("inner2");
  var agg = new AggregateError([inner1, inner2], "aggregate message");
  agg.extraProp = "should be stripped";
  var copy = structuredClone(agg);
  assertInstanceof(copy, AggregateError);
  assertEquals(copy.message, "aggregate message", "AggregateError message");
  assertEquals(copy.errors.length, 2, "AggregateError errors length");
  assertInstanceof(copy.errors[0], Error);
  assertEquals(copy.errors[0].message, "inner1", "AggregateError errors[0] message");
  assertInstanceof(copy.errors[1], TypeError);
  assertEquals(copy.errors[1].message, "inner2", "AggregateError errors[1] message");
  assertEquals(copy.extraProp, undefined, "AggregateError custom property stripped");
})();

(function TestAggregateErrorCauseChain() {
  var cause = new Error("root cause");
  var err = new TypeError("outer", {cause: cause});
  var copy = structuredClone(err);
  assertInstanceof(copy, TypeError);
  assertInstanceof(copy.cause, Error);
  assertEquals(copy.cause.message, "root cause", "Error cause message preserved");
  assertTrue(copy.cause !== cause, "Error cause deep cloned");
})();

(function TestNullPrototype() {
  var obj = Object.create(null);
  obj.key = "value";
  var copy = structuredClone(obj);
  // Per WHATWG OrdinaryCreateFromConstructor, cloned plain objects always get Object.prototype
  assertEquals(Object.prototype, Object.getPrototypeOf(copy), "Null prototype becomes Object.prototype");
  assertEquals("value", copy.key, "Null prototype object property preserved");
  assertTrue(typeof copy.hasOwnProperty === "function", "Cloned object has Object methods");
})();

(function TestMultipleTypedArrayViewsSameBuffer() {
  var buf = new ArrayBuffer(16);
  var u8 = new Uint8Array(buf);
  var i16 = new Int16Array(buf);
  u8[0] = 1;
  u8[1] = 2;
  var expectedI16 = i16[0]; // Capture original value (endianness-dependent)
  var obj = {u8: u8, i16: i16};
  var copy = structuredClone(obj);
  assertInstanceof(copy.u8, Uint8Array);
  assertInstanceof(copy.i16, Int16Array);
  // Both views share the SAME cloned buffer
  assertTrue(copy.u8.buffer === copy.i16.buffer, "Multiple views share cloned buffer");
  assertEquals(16, copy.u8.buffer.byteLength, "Shared cloned buffer byteLength");
  assertEquals(1, copy.u8[0], "Shared buffer data via Uint8Array");
  assertEquals(expectedI16, copy.i16[0], "Shared buffer data via Int16Array");
})();

(function TestErrorCauseRecursive() {
  var e1 = new Error("e1");
  var e2 = new Error("e2", {cause: e1});
  var e3 = new Error("e3", {cause: e2});
  var copy = structuredClone(e3);
  assertEquals(copy.message, "e3", "Top-level error message");
  assertEquals(copy.cause.message, "e2", "First cause message");
  assertEquals(copy.cause.cause.message, "e1", "Second cause message");
  assertTrue(copy.cause !== e2, "Cause chain deep cloned");
  assertTrue(copy.cause.cause !== e1, "Cause chain deep cloned level 2");
})();

(function TestMapWithCyclicReferences() {
  var m = new Map();
  m.set("self", m);
  var copy = structuredClone(m);
  assertInstanceof(copy, Map);
  assertTrue(copy.get("self") === copy, "Map cyclic reference to self preserved");
})();

(function TestSetWithCyclicMap() {
  var m = new Map();
  var s = new Set();
  m.set("set", s);
  s.add(m);
  var copyM = structuredClone(m);
  assertInstanceof(copyM, Map);
  assertInstanceof(copyM.get("set"), Set);
  assertTrue(copyM.get("set").has(copyM), "Cross-reference between Map and Set preserved");
})();

print("Structured clone WPT tests completed successfully");
"success";
