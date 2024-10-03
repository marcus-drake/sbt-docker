package sbtdocker

import sbt.Logger

import sys.process.{Process, ProcessLogger}

object DockerTag {

  def apply(id: ImageId, name: ImageName, dockerPath: String, log: Logger): Unit = {
    val processLogger = ProcessLogger(
      { line =>
        log.info(line)
      },
      { line =>
        log.info(line)
      }
    )

    log.info(s"Tagging image $id with name: $name")

    val command = dockerPath :: "tag" :: id.id :: name.toString :: Nil
    log.debug(s"Running command: '${command.mkString(" ")}'")

    val processOutput = Process(command).lineStream(processLogger)
    processOutput.foreach { line =>
      log.info(line)
    }
  }
}
