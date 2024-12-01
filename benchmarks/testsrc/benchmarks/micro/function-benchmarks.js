function assertEquals(x, y) {
  if (x !== y) {
    throw "Expected ===, but wasn't";
  }
}

function testCallZero() {
  let f = callZero;
  assertEquals(0, f());
}

function testCallOne() {
  let f = callOne;
  assertEquals(1, f(1));
}

function testCallTwo() {
  let f = callTwo;
  assertEquals(4, f(2, 2));
}

function testCallZeroOpt() {
  let f = callZeroOpt;
  assertEquals(0, f());
}

function testCallOneOpt() {
  let f = callOneOpt;
  assertEquals(1, f(1));
}

function testCallTwoOpt() {
  let f = callTwoOpt;
  assertEquals(4, f(2, 2));
}
