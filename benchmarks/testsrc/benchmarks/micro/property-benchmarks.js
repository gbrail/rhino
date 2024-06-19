'use strict';

function createObject(name) {
  return {
    name: name,
    foo: 1,
    bar: 2,
    baz: 3,
  }
}

function createAlternateObject(name) {
  return {
    baz: 3,
    bar: 2,
    foo: 1,
    name: name,
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

function getName(o, count) {
  var name;
  for (var i = 0; i < count; i++) {
    name = o.name;
  }
  return name;
}

function check(o, count) {
  var x;
  for (var i = 0; i < count; i++) {
    x = o.foo + o.bar;
    if (x !== 3) {
      throw "Expected 3, got" + x;
    }
  }
  return x;
}

const testObj = createObject();
check(testObj);
check(testObj);

let obj = {
  foo: 1,
  bar: 2
};
for (var i = 0; i < 10; i++) {
  check(obj);
}

let bobj = {
  bar: 1,
  foo: 2
};
for (var i = 0; i < 10; i++) {
  check(bobj);
}
