/*
 * Test aspects of property operation that are affected by the
 * fast property optimizations. This is designed to be run with
 * RHINO_DEBUG_PROPERTIES=true to manually verify what's happening.
 */
load("testsrc/assert.js");

function checkFooness(x) {
  return x.foo === 'foo';
}

// Create an object with a shape and use it in a function that will
// check its properties.
let o1 = { foo: 'foo', bar: 'bar', baz: 'baz' }
assertTrue(checkFooness(o1));
// Make sure changing it works
o1.foo = 'bar';
assertFalse(checkFooness(o1));

// Create an object of a different shape.
let o2 = {};
o2.baz = 'baz';
o2.bar = 'bar';
o2.foo = 'foo';
assertTrue(checkFooness(o2));

// And go back
o1.foo = 'foo';
assertTrue(checkFooness(o1));

// Do it in a loop, should not see relinking
for (var i = 0; i < 100; i++) {
    let o3 = { foo: 'foo', bar: 'bar', baz: 'baz' }
    assertTrue(checkFooness(o1));
    assertTrue(checkFooness(o2));
    assertTrue(checkFooness(o3));
}

// Set a function property, should also see fast linking there
let of1 = {
  foo: 'foo', bar: 'bar', baz: 'baz',
  checkBarness: function() { return this.bar == 'bar'; }
}
// Should be a fast property get here too
assertTrue(of1.checkBarness());

// Set up an object prototype, make fast linking work here too
function FastMaker() {
  this.foo = 'foo';
  this.bar = 'bar';
  this.baz = 'baz';
}

FastMaker.prototype.checkBazness = function() { return this.baz == 'baz'; }

// Loop calling the prototype, should see no relinking
for (var i = 0; i < 100; i++) {
  let fm1 = new FastMaker();
  assertTrue(fm1.checkBazness());
  fm1.baz = 'bar';
  assertFalse(fm1.checkBazness());
}

// One more time, but obscure the prototype function
let fm2 = new FastMaker();
assertTrue(fm2.checkBazness());
fm2.checkBazness = function() { return 123; }
assertEquals(123, fm2.checkBazness());

'success';
