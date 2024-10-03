package sbtdocker

import java.io.File

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import sbt.IO
import sbtdocker.Helpers._
import sbtdocker.Instructions._
import sbtdocker.staging.{CopyFile, StagedDockerfile}

class DockerBuildSpec extends AnyFreeSpec with Matchers {

  "Stage files" - {
    "Files should be staged" in {
      IO.withTemporaryDirectory { origDir =>
        IO.withTemporaryDirectory { stageDir =>
          val fileA = origDir / "fileA"
          val fileAData = createFile(fileA)

          val stagedDockerfile = StagedDockerfile(
            instructions = Seq.empty,
            stageFiles = Set(
              CopyFile(fileA) -> (stageDir / "fileA")
            )
          )
          DockerBuild.prepareFiles(stagedDockerfile)

          IO.read(stageDir / "fileA") shouldEqual fileAData
        }
      }
    }

    "A Dockerfile should be created" in {
      IO.withTemporaryDirectory { stageDir =>
        val stagedDockerfile = StagedDockerfile(
          instructions = Seq(
            From("ubuntu"),
            Run("echo 123")
          ),
          stageFiles = Set.empty
        )
        DockerBuild.createDockerfile(stagedDockerfile, stageDir)

        val file = stageDir / "Dockerfile"
        file.exists() shouldEqual true
        IO.read(file) shouldEqual "FROM ubuntu\nRUN echo 123"
      }
    }

    def createFile(file: File): String = {
      val fileData = file.getPath
      file.getParentFile.mkdirs()
      assume(file.createNewFile())
      IO.write(file, fileData)
      fileData
    }
  }

  "Build flags" - {
    "Default options should be: use caching, pull if missing and remove on success" in {
      val options = BuildOptions()

      options.cache shouldEqual true
      options.pullBaseImage shouldEqual BuildOptions.Pull.IfMissing
      options.removeIntermediateContainers shouldEqual BuildOptions.Remove.OnSuccess

      val flags = DockerBuild.generateBuildOptionFlags(options)

      flags should contain theSameElementsAs Seq("--no-cache=false", "--rm=true")
    }

    "No cache" in {
      val options = BuildOptions(cache = false)
      val flags = DockerBuild.generateBuildOptionFlags(options)

      flags should contain("--no-cache=true")
    }

    "Always remove" in {
      val options = BuildOptions(removeIntermediateContainers = BuildOptions.Remove.Always)
      val flags = DockerBuild.generateBuildOptionFlags(options)

      flags should contain("--force-rm=true")
    }

    "Never remove" in {
      val options = BuildOptions(removeIntermediateContainers = BuildOptions.Remove.Never)
      val flags = DockerBuild.generateBuildOptionFlags(options)

      flags should contain("--rm=false")
    }

    "Always pull" in {
      val options = BuildOptions(pullBaseImage = BuildOptions.Pull.Always)
      val flags = DockerBuild.generateBuildOptionFlags(options)

      flags should contain("--pull")
    }

    "Add platform argument for cross build" in {
      val options = BuildOptions(platforms = List("linux/amd64", "linux/arm64"))
      val flags = DockerBuild.generateBuildOptionFlags(options)

      flags should contain("--platform=linux/amd64,linux/arm64")

    }

    "Custom arguments" in {
      val options = BuildOptions(additionalArguments = Seq("--add-host", "127.0.0.1:12345", "--compress"))
      val flags = DockerBuild.generateBuildOptionFlags(options)

      flags should contain inOrderElementsOf Seq("--add-host", "127.0.0.1:12345", "--compress")
    }
  }

  "Parse image id" - {
    "Docker build output" in {
      val lines = Seq(
        "Removing intermediate container ba85d1deadeb",
        " ---> 353fcb84af6b",
        "Successfully built 353fcb84af6b",
        "Successfully tagged test:latest"
      )
      DockerBuild.parseImageId(lines) shouldEqual Some(ImageId("353fcb84af6b"))
    }

    "Docker buildx output" in {
      val lines = Seq(
        "#7 exporting layers 0.4s done",
        "#7 exporting manifest sha256:427ff04564194e7d44a5790d5739465789861cb5ee6db60ccdb15388865dfd64 0.0s done",
        "#7 exporting config sha256:d1d7dbb3987417e91239032f2a36a2ede76e62276849ebf362004c14d6fc82ac",
        "#7 exporting config sha256:d1d7dbb3987417e91239032f2a36a2ede76e62276849ebf362004c14d6fc82ac 0.0s done",
        "#7 sending tarball"
      )
      DockerBuild.parseImageId(lines) shouldEqual Some(ImageId("d1d7dbb3987417e91239032f2a36a2ede76e62276849ebf362004c14d6fc82ac"))

    }

    "Docker buildx output with multi platform build" in {
      val lines = Seq(
        "#1 [internal] load build definition from Dockerfile",
        "#1 transferring dockerfile: 76B done",
        "#1 DONE 0.0s",
        "#2 [linux/amd64 internal] load metadata for docker.io/library/eclipse-temurin:17-jre",
        "#2 ...",
        "#3 [auth] library/eclipse-temurin:pull token for registry-1.docker.io",
        "#3 DONE 0.0s",
        "#4 [linux/arm64 internal] load metadata for docker.io/library/eclipse-temurin:17-jre",
        "#4 ...",
        "#2 [linux/amd64 internal] load metadata for docker.io/library/eclipse-temurin:17-jre",
        "#2 DONE 3.3s",
        "#5 [internal] load .dockerignore",
        "#5 transferring context: 2B done",
        "#5 DONE 0.0s",
        "#4 [linux/arm64 internal] load metadata for docker.io/library/eclipse-temurin:17-jre",
        "#4 DONE 3.4s",
        "#6 [linux/arm64 1/1] FROM docker.io/library/eclipse-temurin:17-jre@sha256:5bc826c8e0e248515161ebdb3cc7ea3f50a09d5570155493f7a933bcb5f7a644",
        "#6 resolve docker.io/library/eclipse-temurin:17-jre@sha256:5bc826c8e0e248515161ebdb3cc7ea3f50a09d5570155493f7a933bcb5f7a644 done",
        "#6 DONE 0.0s",
        "#7 [linux/amd64 1/1] FROM docker.io/library/eclipse-temurin:17-jre@sha256:5bc826c8e0e248515161ebdb3cc7ea3f50a09d5570155493f7a933bcb5f7a644",
        "#7 resolve docker.io/library/eclipse-temurin:17-jre@sha256:5bc826c8e0e248515161ebdb3cc7ea3f50a09d5570155493f7a933bcb5f7a644 done",
        "#7 DONE 0.0s",
        "#8 exporting to image",
        "#8 exporting layers done",
        "#8 exporting manifest sha256:415511a6b7d9c469b3d55f2a1a731ca825a150b761d6d3a8d693f6933b73543e done",
        "#8 exporting config sha256:b8b30c8f60e3022c3796910d2f6766556241f06b376a1452e1008aad90eb1a56 done",
        "#8 exporting attestation manifest sha256:00307e30e3b303797f6ec731ce97ca6193e01538e1bf2e385e44ce9d7e320e96 done",
        "#8 exporting manifest sha256:7ae21b1e3165c5607bf526507e27c6b2f96370e1e5d2af30e34dadf91a0f2fb6",
        "#8 exporting manifest sha256:7ae21b1e3165c5607bf526507e27c6b2f96370e1e5d2af30e34dadf91a0f2fb6 done",
        "#8 exporting config sha256:c321bebec67be335397d1cef5215319e6c39c180aa45ad35220a2914e97ad569 done",
        "#8 exporting attestation manifest sha256:e7b403b53ce05cb79be85e78042607b589a3abbd5885d116bef8dac120f4824f done",
        "#8 exporting manifest list sha256:e715e540a6e109611077f9577010c953bd8316bd58286fe4fbec2c9f0b034fc1 done",
        "#8 pushing layers",
        "#8 ...",
        "#9 [auth] test:pull,push token for test.com",
        "#9 DONE 0.0s",
        "#8 exporting to image",
        "#8 pushing layers 6.8s done",
        "#8 pushing manifest for test.com/test:latest@sha256:e715e540a6e109611077f9577010c953bd8316bd58286fe4fbec2c9f0b034fc1",
        "#8 pushing manifest for test.com/test:latest@sha256:e715e540a6e109611077f9577010c953bd8316bd58286fe4fbec2c9f0b034fc1 1.9s done",
        "#8 pushing layers 0.7s done",
        "#8 pushing manifest for test.com/test:v1.0.0@sha256:e715e540a6e109611077f9577010c953bd8316bd58286fe4fbec2c9f0b034fc1",
        "#8 pushing manifest for test.com/test:v1.0.0@sha256:e715e540a6e109611077f9577010c953bd8316bd58286fe4fbec2c9f0b034fc1 1.3s done",
        "#8 DONE 10.7s"
      )
      DockerMultiPlatformBuilder.parseImageId(lines) shouldEqual Some(
        ImageId("e715e540a6e109611077f9577010c953bd8316bd58286fe4fbec2c9f0b034fc1")
      )
    }

    "Docker build output version 20.10.10" in {
      val lines = Seq(
        "#6 exporting to image",
        "#6 sha256:e8c613e07b0b7ff33893b694f7759a10d42e180f2b4dc349fb57dc6b71dcab00",
        "#6 exporting layers done",
        "#6 writing image sha256:688f3de768e18bed863a7357e48b3d11a546ec2064cbcce2ffbe63343525a3a0 done",
        "#6 DONE 0.0s"
      )
      DockerBuild.parseImageId(lines) shouldEqual Some(ImageId("688f3de768e18bed863a7357e48b3d11a546ec2064cbcce2ffbe63343525a3a0"))
    }

    "Podman build output" in {
      val lines = Seq(
        "--> dada5485d85",
        "",
        "Successfully tagged localhost/testbuild:latest",
        "",
        "dada5485d85618e75ad1a9772c6c00523f442c8d30487fb7c9f9f9ea544db1db",
        ""
      )
      DockerBuild.parseImageId(lines) shouldEqual Some(ImageId("dada5485d85618e75ad1a9772c6c00523f442c8d30487fb7c9f9f9ea544db1db"))
    }
  }
}
