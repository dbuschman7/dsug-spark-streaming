package me.lightspeed7.dsug.ui

import me.lightspeed7.dsug.Count
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.Matchers._
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ConvertListToMapTest extends FunSuite {

  test("Convert list to a map of keys mapped to counts") {
    val raw = List(Count("CO", 123), Count("UT", 123), Count("CO", 123), Count("AZ", 123), Count("CO", 123), Count("UT", 123))

    val counts = raw.groupBy(_.key).map(c => c._1 -> c._2.foldLeft(0)((s, c) => s + c.count))

    counts.foreach { f =>
      f._1 match {
        case "CO" => f._2 should be(369)
        case "UT" => f._2 should be(246)
        case "AZ" => f._2 should be(123)
      }
    }
  }
}
