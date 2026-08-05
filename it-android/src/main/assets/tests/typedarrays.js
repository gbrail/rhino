// Basic construction

var ui8 = new Uint8Array(8);
assertEquals(8, ui8.length);
for (var i = 0; i < 8; i++) {
  assertEquals(0, ui8[i]);
}

// From array

var ia8 = new Int8Array([1, 2, 3, 4, 5]);
assertEquals(5, ia8.length);
assertEquals(1, ia8[0]);
assertEquals(3, ia8[2]);
assertEquals(5, ia8[4]);

// Uint8Array from array

var ui8copy = new Uint8Array([10, 20, 30]);
assertEquals(3, ui8copy.length);
assertEquals(10, ui8copy[0]);
assertEquals(20, ui8copy[1]);
assertEquals(30, ui8copy[2]);

// Int16Array

var i16 = new Int16Array([100, -200, 300]);
assertEquals(3, i16.length);
assertEquals(100, i16[0]);
assertEquals(-200, i16[1]);
assertEquals(300, i16[2]);

// Uint16Array

var ui16 = new Uint16Array([0, 65535, 1000]);
assertEquals(3, ui16.length);
assertEquals(0, ui16[0]);
assertEquals(65535, ui16[1]);
assertEquals(1000, ui16[2]);

// Int32Array

var i32 = new Int32Array([0, -1, 2147483647, -2147483648]);
assertEquals(4, i32.length);
assertEquals(0, i32[0]);
assertEquals(-1, i32[1]);
assertEquals(2147483647, i32[2]);
assertEquals(-2147483648, i32[3]);

// Uint32Array

var ui32 = new Uint32Array([0, 1, 4294967295]);
assertEquals(3, ui32.length);
assertEquals(0, ui32[0]);
assertEquals(1, ui32[1]);
assertEquals(4294967295, ui32[2]);

// Float32Array

var f32 = new Float32Array([1.5, -2.5, 0]);
assertEquals(3, f32.length);
assertEqualsDelta(1.5, f32[0], 0.001);
assertEqualsDelta(-2.5, f32[1], 0.001);
assertEquals(0, f32[2]);

// Float64Array

var f64 = new Float64Array([1.1, -2.2, 3.3]);
assertEquals(3, f64.length);
assertEqualsDelta(1.1, f64[0], 0.001);
assertEqualsDelta(-2.2, f64[1], 0.001);
assertEqualsDelta(3.3, f64[2], 0.001);

// Assignment

var buf = new Uint8Array(4);
buf[0] = 1;
buf[1] = 2;
buf[2] = 3;
buf[3] = 4;
assertEquals(1, buf[0]);
assertEquals(2, buf[1]);
assertEquals(3, buf[2]);
assertEquals(4, buf[3]);

// Overflow wrapping for unsigned types

var wrap = new Uint8Array(1);
wrap[0] = 256;
assertEquals(0, wrap[0]);
wrap[0] = -1;
assertEquals(255, wrap[0]);

// Overflow wrapping for signed int8

var wrapSigned = new Int8Array(1);
wrapSigned[0] = 128;
assertEquals(-128, wrapSigned[0]);
wrapSigned[0] = -129;
assertEquals(127, wrapSigned[0]);

// Length is read-only

var lenTest = new Uint8Array(5);
lenTest.length = 10;
assertEquals(5, lenTest.length);

// Constructor properties

assertEquals(1, Int8Array.BYTES_PER_ELEMENT);
assertEquals(1, Uint8Array.BYTES_PER_ELEMENT);
assertEquals(2, Int16Array.BYTES_PER_ELEMENT);
assertEquals(2, Uint16Array.BYTES_PER_ELEMENT);
assertEquals(4, Int32Array.BYTES_PER_ELEMENT);
assertEquals(4, Uint32Array.BYTES_PER_ELEMENT);
assertEquals(4, Float32Array.BYTES_PER_ELEMENT);
assertEquals(8, Float64Array.BYTES_PER_ELEMENT);

// ArrayBuffer backing

var ab = new ArrayBuffer(16);
var view = new Uint8Array(ab);
assertEquals(16, view.length);
view[0] = 42;
view[15] = 99;
assertEquals(42, view[0]);
assertEquals(99, view[15]);

// slice

var src = new Uint8Array([1, 2, 3, 4, 5]);
var sub = src.slice(1, 4);
assertEquals(3, sub.length);
assertEquals(2, sub[0]);
assertEquals(3, sub[1]);
assertEquals(4, sub[2]);

// set from array

var target = new Uint8Array(5);
target.set([10, 20, 30]);
assertEquals(10, target[0]);
assertEquals(20, target[1]);
assertEquals(30, target[2]);
assertEquals(0, target[3]);
assertEquals(0, target[4]);

// set with offset

target.set([100, 200], 3);
assertEquals(100, target[3]);
assertEquals(200, target[4]);

// toLocaleString

var tl = new Int8Array([1, 2, 3]);
assertEquals('1,2,3', tl.toLocaleString());

// toString

var ts = new Int8Array([10, 20, 30]);
assertEquals('10,20,30', ts.toString());

// empty typed arrays

var empty8 = new Uint8Array(0);
assertEquals(0, empty8.length);
assertEquals('', empty8.toString());

var emptyArr = new Int32Array([]);
assertEquals(0, emptyArr.length);

'success';
