package sbtdocker

import sbt._
import sbtdocker.staging.{DockerfileProcessor, StagedDockerfile}

import scala.sys.process.{Process, ProcessLogger}

object DockerBuild {

  /**
    * Build a Dockerfile using a provided docker binary.
    *
    * @param dockerfile Dockerfile to build
    * @param processor processor to create a staging directory for the Dockerfile
    * @param imageNames names of the resulting image
    * @param stageDir stage dir
    * @param dockerPath path to the docker binary
    * @param buildOptions options for the build command
    * @param log logger
    */
  def apply(
    dockerfile: DockerfileBase,
    processor: DockerfileProcessor,
    imageNames: Seq[ImageName],
    buildOptions: BuildOptions,
    buildArguments: Map[String, String],
    stageDir: File,
    dockerPath: String,
    log: Logger
  ): ImageId = {
    dockerfile match {
      case NativeDockerfile(path) =>
        buildAndTag(imageNames, path, dockerPath, buildOptions, buildArguments, log)

      case dockerfileLike: DockerfileLike =>
        val staged = processor(dockerfileLike, stageDir)

        apply(staged, imageNames, buildOptions, buildArguments, stageDir, dockerPath, log)
    }
  }

  /**
    * Build a Dockerfile using a provided docker binary.
    *
    * @param staged a staged Dockerfile to build.
    * @param imageNames names of the resulting image
    * @param stageDir stage dir
    * @param dockerPath path to the docker binary
    * @param buildOptions options for the build command
    * @param log logger
    */
  def apply(
    staged: StagedDockerfile,
    imageNames: Seq[ImageName],
    buildOptions: BuildOptions,
    buildArguments: Map[String, String],
    stageDir: File,
    dockerPath: String,
    log: Logger
  ): ImageId = {
    log.debug("Building Dockerfile:\n" + staged.instructionsString)

    log.debug(s"Preparing stage directory '${stageDir.getPath}'")

    clean(stageDir)
    val dockerfilePath = createDockerfile(staged, stageDir)
    prepareFiles(staged)
    buildAndTag(imageNames, dockerfilePath, dockerPath, buildOptions, buildArguments, log)
  }

  private[sbtdocker] def clean(stageDir: File) = {
    IO.delete(stageDir)
  }

  private[sbtdocker] def createDockerfile(staged: StagedDockerfile, stageDir: File): File = {
    val dockerfilePath = stageDir / "Dockerfile"
    IO.write(dockerfilePath, staged.instructionsString)
    dockerfilePath
  }

  private[sbtdocker] def prepareFiles(staged: StagedDockerfile) = {
    staged.stageFiles.foreach {
      case (source, destination) =>
        source.stage(destination)
    }
  }

  private[sbtdocker] def buildAndTag(
    imageNames: Seq[ImageName],
    dockerfilePath: File,
    dockerPath: String,
    buildOptions: BuildOptions,
    buildArguments: Map[String, String],
    log: Logger
  ): ImageId = {
    val imageId = build(dockerfilePath, dockerPath, buildOptions, buildArguments, log)

    imageNames.foreach { name =>
      DockerTag(imageId, name, dockerPath, log)
    }

    imageId
  }

  private[sbtdocker] def build(
    dockerfilePath: File,
    dockerPath: String,
    buildOptions: BuildOptions,
    buildArguments: Map[String, String],
    log: Logger
  ): ImageId = {
    val dockerfileAbsolutePath = dockerfilePath.getAbsoluteFile
    var lines = Seq.empty[String]

    def runBuild(buildKitSupport: Boolean): Int = {
      val buildX = if (buildOptions.platforms.isEmpty) Nil else List("buildx")
      val load = if (buildOptions.platforms.isEmpty) Nil else List("--load")
      val buildOptionFlags = generateBuildOptionFlags(buildOptions)
      val buildKitFlags = if (buildKitSupport) List("--progress=plain") else Nil
      val buildArgumentFlags = buildArguments.toList.flatMap {
        case (key, value) =>
          Seq(s"--build-arg", s"$key=$value")
      }
      val command: Seq[String] = dockerPath ::
        buildX :::
        "build" ::
        buildOptionFlags :::
        buildKitFlags :::
        buildArgumentFlags :::
        load :::
        "--file" ::
        dockerfileAbsolutePath.getPath ::
        dockerfileAbsolutePath.getParentFile.getPath ::
        Nil
      log.debug(s"Running command: '${command.mkString(" ")}'")

      Process(command, dockerfileAbsolutePath.getParentFile).!(
        ProcessLogger(
          { line =>
            log.info(line)
            lines :+= line
          },
          { line =>
            log.info(line)
            lines :+= line
          }
        )
      )
    }

    val exitCode = {
      val firstBuildExitCode = runBuild(true)
      if (firstBuildExitCode != 0 && lines.contains("unknown flag: --progress")) {
        // Re-runs the build without the --progress flag, in order to support old Docker versions.
        runBuild(false)
      } else {
        firstBuildExitCode
      }
    }

    if (exitCode == 0) {
      val imageId = parseImageId(lines)

      imageId match {
        case Some(id) =>
          id
        case None =>
          throw new DockerBuildException("Could not parse Docker image id")
      }
    } else {
      throw new DockerBuildException(s"Failed to build Docker image (exit code: $exitCode)")
    }
  }

  private[sbtdocker] def generateBuildOptionFlags(buildOptions: BuildOptions): List[String] = {
    val cacheFlag = "--no-cache=" + !buildOptions.cache
    val removeFlag = {
      buildOptions.removeIntermediateContainers match {
        case BuildOptions.Remove.Always =>
          "--force-rm=true"
        case BuildOptions.Remove.Never =>
          "--rm=false"
        case BuildOptions.Remove.OnSuccess =>
          "--rm=true"
      }
    }
    val pullFlag = buildOptions.pullBaseImage match {
      case BuildOptions.Pull.Always => List("--pull")
      case BuildOptions.Pull.IfMissing => Nil
    }
    val platformsFlag: List[String] = buildOptions.platforms match {
      case Seq() => Nil
      case platforms => List(s"--platform=${platforms.mkString(",")}")
    }

    cacheFlag :: removeFlag :: pullFlag ::: platformsFlag ::: buildOptions.additionalArguments.toList
  }

  private val SuccessfullyBuilt = "^Successfully built ([0-9a-f]+)$".r
  private val SuccessfullyBuiltBuildKit = ".* writing image sha256:([0-9a-f]+) .*\\bdone$".r
  private val SuccessfullyBuiltContainerd = ".* exporting manifest list sha256:([0-9a-f]+) .*\\bdone$".r
  private val SuccessfullyBuiltBuildx = ".* exporting config sha256:([0-9a-f]+) .*\\bdone$".r
  private val SuccessfullyBuiltPodman = "^([0-9a-f]{64})$".r
  private val SuccessfullyBuiltNerdctl = "^Loaded image: .*sha256:([0-9a-f]+)$".r

  private[sbtdocker] def parseImageId(lines: Seq[String]): Option[ImageId] = {
    lines.collect {
      case SuccessfullyBuilt(id) => ImageId(id)
      case SuccessfullyBuiltBuildKit(id) => ImageId(id)
      case SuccessfullyBuiltContainerd(id) => ImageId(id)
      case SuccessfullyBuiltBuildx(id) => ImageId(id)
      case SuccessfullyBuiltPodman(id) => ImageId(id)
      case SuccessfullyBuiltNerdctl(id) => ImageId(id)
    }.lastOption
  }
}

class DockerBuildException(message: String) extends RuntimeException(message)
