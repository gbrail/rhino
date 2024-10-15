'use strict';

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

const CONST_COUNT = 10000;
const CONST_INCREMENT = 5;
var VAR_COUNT = 10000;
var VAR_INCREMENT = 5;

function loopVariable() {
    let x = 0;
    for (let i = 0; i < VAR_COUNT; i++) {
        x += VAR_INCREMENT;
    }
    if (x !== VAR_INCREMENT * VAR_COUNT) {
        throw 'Expected x to be ' + (VAR_INCREMENT + VAR_COUNT)
          + ' got ' + x;
    }
    return x;
}

function loopConstant() {
    let x = 0;
    for (let i = 0; i < CONST_COUNT; i++) {
        x += CONST_INCREMENT;
    }
    if (x !== CONST_INCREMENT * CONST_COUNT) {
        throw 'Expected x to be ' + (CONST_INCREMENT + CONST_COUNT)
          + ' got ' + x;
    }
    return x;
}

function createArray() {
    let a = new Array(CONST_COUNT);
    for (let i = 0; i < CONST_COUNT; i++) {
        a[i] = CONST_INCREMENT;
    }
    return a;
}

function loopArray(a) {
    let x = 0;
    for (let i = 0; i < a.length; i++) {
        x += a[i];
    }
    if (x !== CONST_INCREMENT * CONST_COUNT) {
        throw 'Expected x to be ' + (CONST_INCREMENT + CONST_COUNT)
          + ' got ' + x;
    }
    return x;
}