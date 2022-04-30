package spyreDb.db

import zio.{test as _, *}
import zio.test.*
import zio.test.Assertion.*

object ReadWriteLockTests extends DefaultRunnableSpec {

  private def read(ref: Ref[List[Int]], rwl: ReadWriteLock[Unit], i: Int): URIO[Clock, Fiber[Nothing, Int]] =
    rwl.read {
      ref.update(i :: _).as(i).delay(Duration.fromMillis(500))
    }.fork <* Clock.sleep(Duration.fromMillis(50))

  private def write(ref: Ref[List[Int]], rwl: ReadWriteLock[Unit], i: Int): URIO[Clock, Fiber[Nothing, Int]] =
    rwl.write {
      ref.update(i :: _).as(i).delay(Duration.fromMillis(500))
    }.fork <* Clock.sleep(Duration.fromMillis(50))

  private def readZIO[E, A](rwl: ReadWriteLock[Unit], zio: IO[E, A]): URIO[Clock, Fiber[E, A]] =
    rwl.read {
      zio.delay(Duration.fromMillis(500))
    }.fork <* Clock.sleep(Duration.fromMillis(50))

  private def writeZIO[E, A](rwl: ReadWriteLock[Unit], zio: IO[E, A]): URIO[Clock, Fiber[E, A]] =
    rwl.write {
      zio.delay(Duration.fromMillis(500))
    }.fork <* Clock.sleep(Duration.fromMillis(50))

  override def spec: ZSpec[TestEnvironment, Any] =
    suite("ReadWriteLockTests")(
      test("read works") {
        for {
          res <- ReadWriteLock.make[Unit].read(ZIO.succeed(1))
        } yield assert(res)(equalTo(1))
      },
      test("write works") {
        for {
          res <- ReadWriteLock.make[Unit].write(ZIO.succeed(1))
        } yield assert(res)(equalTo(1))
      },
      test("read + write works") {
        for {
          res1 <- ReadWriteLock.make[Unit].read(ZIO.succeed(1))
          res2 <- ReadWriteLock.make[Unit].write(ZIO.succeed(2))
        } yield assert(res1)(equalTo(1)) && assert(res2)(equalTo(2))
      },
      test("write + read works") {
        for {
          res1 <- ReadWriteLock.make[Unit].write(ZIO.succeed(1))
          res2 <- ReadWriteLock.make[Unit].read(ZIO.succeed(2))
        } yield assert(res1)(equalTo(1)) && assert(res2)(equalTo(2))
      },
      test("runs in order") {
        for {
          ref <- Ref.make(List.empty[Int])
          rwl = ReadWriteLock.make[Unit]
          f1 <- read(ref, rwl, 1)
          f2 <- write(ref, rwl, 2)
          f3 <- read(ref, rwl, 3)
          f4 <- write(ref, rwl, 4)
          r1 <- f1.join
          r2 <- f2.join
          r3 <- f3.join
          r4 <- f4.join
          list <- ref.get.map(_.reverse)
        } yield assert(r1)(equalTo(1)) &&
          assert(r2)(equalTo(2)) &&
          assert(r3)(equalTo(3)) &&
          assert(r4)(equalTo(4)) &&
          assert(list)(equalTo(List(1, 2, 3, 4)))
      } @@ TestAspect.timeout(Duration.fromMillis(3000)),
      test("reads can happen in parallel") {
        for {
          ref <- Ref.make(List.empty[Int])
          rwl = ReadWriteLock.make[Unit]
          (duration, _) <- (for {
            f1 <- read(ref, rwl, 1)
            f2 <- read(ref, rwl, 2)
            f3 <- read(ref, rwl, 3)
            f4 <- read(ref, rwl, 4)
            _ <- Fiber.joinAll(Seq(f1, f2, f3, f4))
          } yield ()).timed
          list <- ref.get.map(_.reverse)
        } yield assert(duration)(isGreaterThanEqualTo(Duration.fromMillis(500))) &&
          assert(duration)(isLessThan(Duration.fromMillis(1000))) &&
          assert(list)(equalTo(List(1, 2, 3, 4)))
      },
      test("proper blocking") {
        for {
          ref <- Ref.make(List.empty[Int])
          rwl = ReadWriteLock.make[Unit]
          (duration, list) <- (for {
            f1 <- write(ref, rwl, 1)
            f2 <- read(ref, rwl, 2)
            f3 <- read(ref, rwl, 3)
            f4 <- read(ref, rwl, 4)
            f5 <- write(ref, rwl, 5)
            f6 <- read(ref, rwl, 6)
            f7 <- read(ref, rwl, 7)
            f8 <- read(ref, rwl, 8)
            f9 <- write(ref, rwl, 9)
            _ <- Fiber.joinAll(Seq(f1, f2, f3, f4, f5, f6, f7, f8, f9))
            list <- ref.get.map(_.reverse)
          } yield list).timed
          assertRange = (from: Int, until: Int) => assert(list.slice(from, until).sorted)(equalTo((from + 1).to(until).toList))
        } yield assert(duration)(isGreaterThanEqualTo(Duration.fromMillis(2500))) &&
          assert(duration)(isLessThan(Duration.fromMillis(3000))) &&
          assertRange(0, 1) &&
          assertRange(1, 4) &&
          assertRange(4, 5) &&
          assertRange(5, 8) &&
          assertRange(8, 9)
      },
      test("continues on errors") {
        val rwl = ReadWriteLock.make[Unit]
        for {
          f1 <- writeZIO(rwl, ZIO.fail(1))
          f2 <- writeZIO(rwl, ZIO.dieMessage("die"))
          f3 <- writeZIO(rwl, ZIO.succeed(2))
          r1 <- f1.join.cause
          r2 <- f2.join.cause
          r3 <- f3.join.cause
        } yield assertTrue(r1.isFailure) &&
          assertTrue(r2.isDie) &&
          assertTrue(r3.isEmpty)
      },
      test("continues on grouped errors") {
        val rwl = ReadWriteLock.make[Unit]
        for {
          f1 <- writeZIO(rwl, ZIO.fail(1))
          f2 <- readZIO(rwl, ZIO.fail(2))
          f3 <- readZIO(rwl, ZIO.dieMessage("die").ignore)
          f4 <- readZIO(rwl, ZIO.succeed(3))
          f5 <- writeZIO(rwl, ZIO.succeed(4))
          r1 <- f1.join.cause
          r2 <- f2.join.cause
          r3 <- f3.join.cause
          r4 <- f4.join.cause
          r5 <- f5.join.cause
        } yield assertTrue(r1.isFailure) &&
          assertTrue(r2.isFailure) &&
          assertTrue(r3.isDie) &&
          assertTrue(r4.isEmpty) &&
          assertTrue(r5.isEmpty)
      },
    ) @@ TestAspect.withLiveEnvironment

}
