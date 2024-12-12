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

    "Docker build with containerd output" in {
      val lines = Seq(
        "[info] #10 exporting layers 2.8s done",
        "[info] #10 exporting manifest sha256:7f8c52951645245ac6bdc0e213330a88834ebcce340b32a834287bb6bfa6d5cb 0.0s done",
        "[info] #10 exporting config sha256:9f7f478055830d4b6f0981b76dcb33be52c1ef1496c71b2dbbecf7336ede58a2 0.0s done",
        "[info] #10 exporting attestation manifest sha256:ba07dabd29c79fa4f8f55b2d691cc740638e4b4d6abe6eb07aa8774f0b203cc1",
        "[info] #10 exporting attestation manifest sha256:ba07dabd29c79fa4f8f55b2d691cc740638e4b4d6abe6eb07aa8774f0b203cc1 0.1s done",
        "[info] #10 exporting manifest list sha256:bb462cb506fd951efc8847f7f4c9a227b5908246101c1d9e5685daf632ce9c51 0.0s done"
      )
      DockerBuild.parseImageId(lines) shouldEqual Some(ImageId("bb462cb506fd951efc8847f7f4c9a227b5908246101c1d9e5685daf632ce9c51"))
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
