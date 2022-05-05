package spyreDb.db

import java.io.File as JavaFile
import java.util.UUID
import klib.utils.*
import zio.{test as _, *}
import zio.test.*
import zio.test.Assertion.*

object ByteFileTests extends RunnableSpec[Executable.BaseEnv, Any] {

  override def aspects: List[TestAspectAtLeastR[Executable.Env]] = Nil

  override def runner: TestRunner[Executable.BaseEnv, Any] =
    TestRunner(
      TestExecutor.default {
        (FileSystem.live ++
          Logger.live(Logger.LogLevel.Debug) ++
          ZLayer.succeed(RunMode.Dev) ++
          testEnvironment >+>
          TestRandom.random).orDieKlib
      },
    )

  private def getRandomByteFile: RIOM[Scope & Executable.BaseEnv, ByteFile] =
    for {
      tmpDir <- ZIOM.attempt(java.lang.System.getProperty("java.io.tmpdir")).flatMap(File.fromPath)
      uuid <- ZIO.succeed(UUID.randomUUID)
      tmpFile <- tmpDir.child(uuid.toString)
      byteFile <- ByteFile.open(tmpFile, "rw")
    } yield byteFile

  private def makeTest(name: String)(testFunction: ByteFile => ZIO[Executable.BaseEnv, Any, TestResult]): ZSpec[Executable.BaseEnv, Any] =
    test(name) {
      ZIO.scoped {
        ZIO.acquireRelease(getRandomByteFile)(byteFile => ZIOM.attempt(byteFile.file.delete).orDieKlib).flatMap(testFunction)
      }
    }

  override def spec: ZSpec[Executable.BaseEnv, Any] =
    suite("ByteFileTests")(
      makeTest("can create file") { _ => ZIO.succeed(assertCompletes) },
      makeTest("1st write is to position 8") {
        _.writeString("Test").map(assert(_)(equalTo(8L)))
      },
      makeTest("2nd write is greater than 8") { byteFile =>
        for {
          _ <- byteFile.writeString("Test")
          addr <- byteFile.writeString("Test2")
        } yield assert(addr)(isGreaterThan(8L))
      },
      makeTest("writes to free list if small enough") { byteFile =>
        for {
          addr <- byteFile.writeString("Test")
          _ <- byteFile.writeString("Test2")
          _ <- byteFile.free(addr)
          addr <- byteFile.writeString("A")
        } yield assert(addr)(equalTo(8L))
      },
      makeTest("writes to eof if too big") { byteFile =>
        for {
          addr <- byteFile.writeString("Test")
          _ <- byteFile.writeString("Test2")
          _ <- byteFile.free(addr)
          addr <- byteFile.writeString("AAAAAA")
        } yield assert(addr)(isGreaterThan(8L))
      },
      makeTest("can read what is written") { byteFile =>
        for {
          addr <- byteFile.writeString("Test")
          str <- byteFile.readString(addr)
        } yield assert(str)(equalTo("Test"))
      },
      makeTest("can't read from freed addr") { byteFile =>
        for {
          addr <- byteFile.writeString("Test")
          _ <- byteFile.free(addr)
          res <- byteFile.readString(addr).either
        } yield assert(res)(isLeft)
      },
    )

}
