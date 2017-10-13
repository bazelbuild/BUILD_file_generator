package com.google.devtools.build.bfg.scala

import org.scalatest.FunSuite

class ScalaSourceFileParserTest extends FunSuite {
    test("scalatest is working") {
      assertResult(1) {
        new ScalaSourceFileParser().returnOne()
      }
    }
}
