package sbtdocker

import sbt._

import scala.io.Source
import scala.sys.process.{Process, ProcessLogger}

object DockerPush {

  /**
    * Push Docker images to a registry.
    *
    * @param dockerPath path to the docker binary
    * @param imageNames names of the images to push
    * @param log logger
    */
  def apply(dockerPath: String, imageNames: Seq[ImageName], log: Logger): Map[ImageName, ImageDigest] = {
    imageNames.map { imageName =>
      apply(dockerPath, imageName, log)
    }.toMap
  }

  /**
    * Push a Docker image to a registry.
    *
    * @param dockerPath path to the docker binary
    * @param imageName name of the image to push
    * @param log logger
    */
  def apply(dockerPath: String, imageName: ImageName, log: Logger): (ImageName, ImageDigest) = {
    log.info(s"Pushing docker image with name: '$imageName'")

    var lines = Seq.empty[String]
    val processLog = ProcessLogger(
      { line =>
        log.info(line)
        lines :+= line
      },
      { line =>
        log.info(line)
        lines :+= line
      }
    )

    val isPodman = dockerPath.contains("podman")
    val digestFileName = "digestFile"

    val command = dockerPath :: "push" :: imageName.toString :: (if (!isPodman) Nil else List("--digestfile", digestFileName))
    log.debug(s"Running command: '${command.mkString(" ")}'")

    val process = Process(command)
    val exitCode = process ! processLog
    if (exitCode != 0) throw new DockerPushException(s"Failed to run 'docker push' on image $imageName. Exit code $exitCode")

    val digestPrefix = if(isPodman) "" else " digest: "
    val PushedImageDigestSha256 = (s".*${digestPrefix}sha256:([0-9a-f]+).*").r
    val imageDigest = (if(isPodman) {
      val source = Source.fromFile(digestFileName)
      val digestLines = source.getLines.toList
      source.close()
      digestLines
    }
    else lines).collect {
      case PushedImageDigestSha256(digest) => ImageDigest("sha256", digest)
    }.lastOption


    imageDigest match {
      case Some(digest) =>
        imageName -> digest
      case None =>
        throw new DockerPushException("Could not parse Docker image digest")
    }
  }
}

class DockerPushException(message: String) extends RuntimeException(message)
