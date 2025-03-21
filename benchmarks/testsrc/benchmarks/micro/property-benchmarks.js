'use strict';

function GlobalTestObject(name) {
    this.name = name;
    this.foo = 1;
    this.bar = 2;
    this.baz = 3;
}

GlobalTestObject.prototype.check = function() {
    if (this.foo + this.bar != this.baz) {
      throw 'Check failed';
    }
}

const GlobalTest = new GlobalTestObject('GlobalTest');

function createObject(name) {
  return {
    name: name,
    foo: 1,
    bar: 2,
    baz: 3,
  }
}

function createObjectFieldByField(name) {
  let o = {};
  o.name = name;
  o.foo = 1;
  o.bar = 2;
  o.baz = 3;
  return o;
}

function getName(o) {
  return o.name;
}

function check(o) {
  const x = o.foo + o.bar;
  if (x !== 3) {
    throw "Expected 3, got" + x;
  }
  return x;
}

function checkGlobal() {
    GlobalTest.check();
}

function test() {
    let o = createObject('foo');
    var name;
    for (var i = 0; i < 10; i++) {
        name = getName(o);
    }
    for (var i = 0; i < 10; i++) {
        check(o);
    }
    for (var i = 0; i < 10; i++) {
        checkGlobal();
    }
}

function test10() {
    for (var i = 0; i < 10; i++) {
        let o = createObject('foo');
        var name = getName(o);
        check(o);
    }
}

test();
//test10();