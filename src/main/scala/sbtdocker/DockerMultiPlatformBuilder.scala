package sbtdocker

import sbt.*
import scala.sys.process.Process
import scala.sys.process

import sbtdocker.ProcessRunner.*

class DockerMultiPlatformBuilder(
  dockerPath: String,
  dockerfilePath: File,
  platforms: Set[Platform],
  imageNames: Seq[ImageName],
  log: Logger
) {
  import DockerMultiPlatformBuilder.*

  private def buildxProcess(args: String): process.ProcessBuilder = {
    val command = s"$dockerPath buildx $args"
    log.debug(s"command: '$command'")
    Process(command)
  }

  private val paramPlatforms = s"--platform=${platforms.map(_.value).mkString(",")}"
  private val paramFile = s"--file ${dockerfilePath.getAbsoluteFile.getPath}"
  private val paramsTags = imageNames.map(_.toString).map(str => s"--tag $str").mkString(" ")
  private val argContext = s"${dockerfilePath.getAbsoluteFile.getParentFile.getPath}"

  private def createDockerBuilder() = buildxProcess {
    s"create --use $paramPlatforms --name $DOCKER_BUILDER_NAME"
  }

  private def inspectDockerBuilder() = buildxProcess {
    s"inspect --bootstrap"
  }

  private def buildDockerImage() = buildxProcess {
    s"build --push --progress=plain $paramPlatforms $paramFile $paramsTags $argContext"
  }

  private def removeDockerBuilder() = buildxProcess {
    s"rm $DOCKER_BUILDER_NAME"
  }

  def run(): ImageId = {

    ProcessRunner.run(removeDockerBuilder())

    val script: process.ProcessBuilder =
      createDockerBuilder() #&&
        inspectDockerBuilder() #&&
        buildDockerImage() #&&
        removeDockerBuilder()

    ProcessRunner.run(script, log) match {
      case (0, logLines) =>
        parseImageId(logLines) match {
          case Some(imageId) =>
            log.info(s"ImageId: ${imageId.id}")
            imageId
          case None => throw new DockerMultiPlatformBuildException(s"Parse imageId failed")
        }
      case (n, _) =>
        throw new DockerMultiPlatformBuildException(s"Build failed, exitCode: $n")
    }

  }

}

object DockerMultiPlatformBuilder {
  private val DOCKER_BUILDER_NAME = "sbtdocker-builder"

  private val imageIdRegex = ".* exporting manifest list sha256:([0-9a-f]{64}).*\\bdone$".r

  def parseImageId(logLines: List[LogLine]): Option[ImageId] =
    parseImageId(logLines.map(_.line))

  def parseImageId(logLines: Seq[String]): Option[ImageId] =
    logLines.collect { case imageIdRegex(id) => ImageId(id) }.lastOption
}

class DockerMultiPlatformBuildException(message: String) extends RuntimeException(message)
