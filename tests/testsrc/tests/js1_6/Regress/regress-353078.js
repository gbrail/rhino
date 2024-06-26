/* -*- Mode: javascript; tab-width: 2; indent-tabs-mode: nil; c-basic-offset: 2 -*- */
/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

var gTestfile = 'regress-353078.js';
//-----------------------------------------------------------------------------
var BUGNUMBER = 353078;
var summary = 'Do not assert with bogus toString, map, split';
var actual = 'No Crash';
var expect = 'No Crash';

//-----------------------------------------------------------------------------
test();
//-----------------------------------------------------------------------------

function test()
{
  enterFunc ('test');
  printBugNumber(BUGNUMBER);
  printStatus (summary);
 
  try
  {
    this.toString = function() { return {}; }; p = [11].map('foo'.split);
  }
  catch(ex)
  {
  }
  reportCompare(expect, actual, summary);

  exitFunc ('test');
}
