package pl.touk.esp.engine.process

import java.util.Date

import org.scalatest.{FlatSpec, Matchers}
import pl.touk.esp.engine.api._
import pl.touk.esp.engine.build.GraphBuilder
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.esp.engine.graph.service.{Parameter, ServiceRef}
import pl.touk.esp.engine.graph.variable.Field
import pl.touk.esp.engine.process.ProcessTestHelpers.{MockService, SimpleRecord, processInvoker}
import pl.touk.esp.engine.spel

import scala.collection.JavaConverters._
import scala.concurrent.duration._

class FlinkProcessRegistrarSpec extends FlatSpec with Matchers {

  import spel.Implicits._

  it should "aggregate and filter records" in {
    val process = EspProcess(MetaData("proc1"),
      ExceptionHandlerRef(List.empty),
      GraphBuilder.source("id", "input")
        .aggregate("agg", "input", "#input.id", 5 seconds, 1 second)
        .filter("filter1", "#sum(#input.![value1]) > 24")
        .processor("proc2", "logService", "all" -> "#distinct(#input.![value2])")
        .sink("out", "monitor"))
    val data = List(
      SimpleRecord("1", 12, "a", new Date(0)),
      SimpleRecord("1", 15, "b", new Date(1000)),
      SimpleRecord("2", 12, "c", new Date(2000)),
      SimpleRecord("1", 23, "d", new Date(5000))
    )

    processInvoker.invoke(process, data)

    MockService.data shouldNot be('empty)
    MockService.data(0) shouldBe Set("a", "b").asJava
  }

  it should "aggregate nested records" in {
    val process = EspProcess(MetaData("proc1"),
      ExceptionHandlerRef(List.empty),
      GraphBuilder.source("id", "input")
        .aggregate("agg", "input", "#input.id", 5 seconds, 1 second)
        .buildVariable("newInput", "newInput",
          "id" -> "#input[0].id",
          "sum" -> "#sum(#input.![value1])"
        )
        .filter("filter1", "#newInput[sum] > 24")
        .aggregate("agg2", "newInput", "#newInput[id]", 5 seconds, 1 second)
        .processor("proc2", "logService", "all" -> "#distinct(#newInput.![[sum]])")
        .sink("out", "monitor"))
    val data = List(
      SimpleRecord("1", 12, "a", new Date(0)),
      SimpleRecord("1", 15, "b", new Date(1000)),
      SimpleRecord("2", 12, "c", new Date(2000)),
      SimpleRecord("1", 23, "d", new Date(5000))
    )

    processInvoker.invoke(process, data)

    MockService.data shouldNot be('empty)
    MockService.data(0) shouldBe Set(27L).asJava
  }

}