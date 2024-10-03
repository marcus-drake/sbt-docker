package sbtdocker

import sbt.Logger
import scala.sys.process
import scala.sys.process.ProcessLogger

object ProcessRunner {

  sealed trait LogLine extends Product with Serializable {
    def line: String
  }

  object LogLine {

    final case class Info(line: String) extends LogLine {
      override def toString: String = s"info | $line"
    }

    final case class Err(line: String) extends LogLine {
      override def toString: String = s"error | $line"
    }

    def info(line: String): LogLine = Info(line)
    def err(line: String): LogLine = Err(line)
  }

  def run(processBuilder: process.ProcessBuilder, log: Logger): (Int, List[LogLine]) = {
    val logLineBuilder = List.newBuilder[LogLine]
    val processLogger: ProcessLogger = ProcessLogger(
      { line =>
        logLineBuilder += LogLine.info(line)
        log.info(line)
      },
      { line =>
        logLineBuilder += LogLine.err(line)
        log.err(line)
      }
    )

    val ret = processBuilder ! processLogger
    val logLines = logLineBuilder.result()
    (ret, logLines)
  }

  def run(processBuilder: process.ProcessBuilder): Int = {
    val processLogger: ProcessLogger = ProcessLogger(
      { _ => () },
      { _ => () }
    )

    processBuilder ! processLogger

  }
}
