/* -*- Mode: javascript; tab-width: 2; indent-tabs-mode: nil; c-basic-offset: 2 -*- */
/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/*
 *
 * Date:    26 September 2003
 * SUMMARY: Regexp conformance test
 *
 * See http://bugzilla.mozilla.org/show_bug.cgi?id=220367
 *
 */
//-----------------------------------------------------------------------------
var gTestfile = 'regress-220367-001.js';
var i = 0;
var BUGNUMBER = 220367;
var summary = 'Regexp conformance test';
var status = '';
var statusmessages = new Array();
var pattern = '';
var patterns = new Array();
var string = '';
var strings = new Array();
var actualmatch = '';
var actualmatches = new Array();
var expectedmatch = '';
var expectedmatches = new Array();


status = inSection(1);
string = 'a';
pattern = /(a)|(b)/;
actualmatch = string.match(pattern);
expectedmatch = Array(string, 'a', undefined);
addThis();

status = inSection(2);
string = 'b';
pattern = /(a)|(b)/;
actualmatch = string.match(pattern);
expectedmatch = Array(string, undefined, 'b');
addThis();



//-----------------------------------------------------------------------------
test();
//-----------------------------------------------------------------------------



function addThis()
{
  statusmessages[i] = status;
  patterns[i] = pattern;
  strings[i] = string;
  actualmatches[i] = actualmatch;
  expectedmatches[i] = expectedmatch;
  i++;
}


function test()
{
  enterFunc ('test');
  printBugNumber(BUGNUMBER);
  printStatus (summary);
  testRegExp(statusmessages, patterns, strings, actualmatches, expectedmatches);
  exitFunc ('test');
}
