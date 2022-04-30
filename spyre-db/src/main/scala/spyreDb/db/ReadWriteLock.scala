package spyreDb.db

import cats.data.NonEmptyList
import cats.syntax.either.*
import cats.syntax.option.*
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import zio.*

// TODO (KR) : Add tracking for when an effect was originally scheduled, and how long it ended up waiting
//           : Then, have the ability for a whole request to track how long it was sleeping
//           : Then, this can be compared against its total runtime
final class ReadWriteLock[L: Tag] {
  import ReadWriteLock.*

  private object internal {

    final case class ReadEffect[E, A](zio: ZIO[ReadAccess[L], E, A], register: IO[E, A] => Any)
    object ReadEffect {
      def fromZIO[R: EnvironmentTag, E, A](zio: ZIO[R & ReadAccess[L], E, A], register: IO[E, A] => Any): URIO[R, ReadEffect[E, A]] =
        ZIO.environment[R].map { r =>
          ReadEffect(zio.provideSomeEnvironment[ReadAccess[L]](_.union(r)), register)
        }
    }

    final case class WriteEffect[E, A](zio: ZIO[WriteAccess[L] & ReadAccess[L], E, A], register: IO[E, A] => Any)
    object WriteEffect {
      def fromZIO[R: EnvironmentTag, E, A](zio: ZIO[R & WriteAccess[L] & ReadAccess[L], E, A], register: IO[E, A] => Any): URIO[R, WriteEffect[E, A]] =
        ZIO.environment[R].map { r =>
          WriteEffect(zio.provideSomeEnvironment[WriteAccess[L] & ReadAccess[L]](_.union(r)), register)
        }
    }

    type EffectQueue = List[Either[ReadEffect[_, _], WriteEffect[_, _]]]

    enum State {
      case Waiting
      case Reading(reading: Set[UUID], rQueue: EffectQueue)
      case Writing(writing: UUID, rQueue: EffectQueue)
    }

    enum UnAppliedNext {
      case ToWait
      case ToRead(reads: NonEmptyList[ReadEffect[_, _]], rQueue: EffectQueue)
      case ToWrite(write: WriteEffect[_, _], rQueue: EffectQueue)
    }

    enum AppliedNext {
      case ToWait
      case ToRead(reads: NonEmptyList[(UUID, ReadEffect[_, _])])
      case ToWrite(write: (UUID, WriteEffect[_, _]))
    }

    private val state: AtomicReference[State] = AtomicReference(State.Waiting)
    @tailrec
    def stateLoop[A](f: State => (State, A)): A = {
      val oldState = state.get
      val (newState, a) = f(oldState)
      if (state.compareAndSet(oldState, newState)) a
      else stateLoop(f)
    }

    private def getUnAppliedNext(rQueue: EffectQueue): UnAppliedNext = {
      @tailrec
      def getAsManyReadsAsPossible(queue: EffectQueue, rReads: NonEmptyList[ReadEffect[_, _]]): UnAppliedNext =
        queue match {
          case Left(readEffect) :: tail => getAsManyReadsAsPossible(tail, readEffect :: rReads)
          case _                        => UnAppliedNext.ToRead(rReads.reverse, queue.reverse)
        }

      rQueue.reverse match {
        case Left(readEffect) :: tail   => getAsManyReadsAsPossible(tail, NonEmptyList.one(readEffect))
        case Right(writeEffect) :: tail => UnAppliedNext.ToWrite(writeEffect, tail.reverse)
        case Nil                        => UnAppliedNext.ToWait
      }
    }

    private def getAppliedNext(rQueue: EffectQueue): (State, AppliedNext) =
      getUnAppliedNext(rQueue) match {
        case UnAppliedNext.ToRead(reads, rQueue) =>
          val applied = reads.map((UUID.randomUUID, _))
          (State.Reading(applied.toList.map(_._1).toSet, rQueue), AppliedNext.ToRead(applied))
        case UnAppliedNext.ToWrite(write, rQueue) =>
          val uuid = UUID.randomUUID
          (State.Writing(uuid, rQueue), AppliedNext.ToWrite((uuid, write)))
        case UnAppliedNext.ToWait => (State.Waiting, AppliedNext.ToWait)
      }

    private def forkExecuteNext(next: AppliedNext): UIO[Unit] =
      next match {
        case AppliedNext.ToRead(reads)                => ZIO.foreachParDiscard(reads.toList) { (uuid, readEffect) => registerReadEffect(uuid, readEffect) }.fork.unit
        case AppliedNext.ToWrite((uuid, writeEffect)) => registerWriteEffect(uuid, writeEffect).fork.unit
        case AppliedNext.ToWait                       => ZIO.unit
      }

    def registerReadEffect(uuid: UUID, readEffect: ReadEffect[_, _]): UIO[Any] = {
      val attemptToCloseRead: UIO[AppliedNext] =
        ZIO.succeed {
          stateLoop {
            case State.Reading(reading, rQueue) =>
              val newReading = reading - uuid
              if (newReading.isEmpty) getAppliedNext(rQueue)
              else (State.Reading(newReading, rQueue), AppliedNext.ToWait)
            case state => throw new RuntimeException(s"Internal Defect [registerReadEffect] : $state")
          }
        }

      ZIO.succeed {
        readEffect.register {
          readEffect.zio.provideLayer(ZLayer.succeed(ReadAccess(uuid))).ensuring {
            attemptToCloseRead.flatMap(forkExecuteNext)
          }
        }
      }
    }

    def registerWriteEffect(uuid: UUID, writeEffect: WriteEffect[_, _]): UIO[Any] = {
      val attemptToCloseWrite: UIO[AppliedNext] =
        ZIO.succeed {
          stateLoop {
            case State.Writing(_, rQueue) => getAppliedNext(rQueue)
            case state                    => throw new RuntimeException(s"Internal Defect [registerWriteEffect] : $state")
          }
        }

      ZIO.succeed {
        writeEffect.register {
          writeEffect.zio.provideLayer(ZLayer.succeed(WriteAccess(uuid)) ++ ZLayer.succeed(ReadAccess(uuid))).ensuring {
            attemptToCloseWrite.flatMap(forkExecuteNext)
          }
        }
      }
    }
  }
  import internal.*

  // If the lock is currently waiting: start this off, and set the state to reading
  // If the lock is currently reading:
  //   If there is nothing in the queue, start this off, and add to the current
  //   If there is something in the queue, add this to the front of the reversed queue
  // If the lock is currently writing: add this to the front of the reversed queue
  def read[R: EnvironmentTag, E, A](zio: ZIO[R & ReadAccess[L], E, A]): ZIO[R, E, A] = {
    def attemptToAcquireReadAccess(readEffect: ReadEffect[E, A]): UIO[Option[UUID]] =
      ZIO.succeed {
        stateLoop {
          case State.Waiting =>
            val readAccess = UUID.randomUUID
            (State.Reading(Set(readAccess), Nil), readAccess.some)
          case State.Reading(reading, rQueue) =>
            if (rQueue.isEmpty) {
              val readAccess = UUID.randomUUID
              (State.Reading(reading + readAccess, Nil), readAccess.some)
            } else (State.Reading(reading, readEffect.asLeft :: rQueue), None)
          case State.Writing(writing, rQueue) =>
            (State.Writing(writing, readEffect.asLeft :: rQueue), None)
        }
      }

    ZIO.asyncZIO[R, E, A] { register =>
      for {
        readEffect <- ReadEffect.fromZIO[R, E, A](zio, register)
        readAccess <- attemptToAcquireReadAccess(readEffect)
        _ <- ZIO.foreach(readAccess)(registerReadEffect(_, readEffect))
      } yield ()
    }
  }

  // If the lock is currently waiting: start this off, and set the state to reading
  // If the lock is currently reading: add this to the front of the reversed queue
  // If the lock is currently writing: add this to the front of the reversed queue
  def write[R: EnvironmentTag, E, A](zio: ZIO[R & WriteAccess[L] & ReadAccess[L], E, A]): ZIO[R, E, A] = {
    def attemptToAcquireWriteAccess(writeEffect: WriteEffect[_, _]): UIO[Option[UUID]] =
      ZIO.succeed {
        stateLoop {
          case State.Waiting =>
            val writeAccess = UUID.randomUUID
            (State.Writing(writeAccess, Nil), writeAccess.some)
          case State.Reading(reading, rQueue) =>
            (State.Reading(reading, writeEffect.asRight :: rQueue), None)
          case State.Writing(writing, rQueue) =>
            (State.Writing(writing, writeEffect.asRight :: rQueue), None)
        }
      }

    ZIO.asyncZIO[R, E, A] { register =>
      for {
        writeEffect <- WriteEffect.fromZIO[R, E, A](zio, register)
        writeAccess <- attemptToAcquireWriteAccess(writeEffect)
        _ <- ZIO.foreachDiscard(writeAccess)(registerWriteEffect(_, writeEffect))
      } yield ()
    }
  }

}
object ReadWriteLock {
  final case class ReadAccess[L: Tag](accessId: UUID)
  final case class WriteAccess[L: Tag](accessId: UUID)

  def apply[L: Tag]: Applied[L] = Applied[L]
  def make[L: Tag]: ReadWriteLock[L] = new ReadWriteLock[L]

  final class Applied[L: Tag] private[ReadWriteLock] {
    def read[R: EnvironmentTag, E, A](zio: ZIO[R & ReadAccess[L], E, A]): ZIO[R & ReadWriteLock[L], E, A] =
      ZIO.service[ReadWriteLock[L]].flatMap(_.read(zio))
    def write[R: EnvironmentTag, E, A](zio: ZIO[R & WriteAccess[L] & ReadAccess[L], E, A]): ZIO[R & ReadWriteLock[L], E, A] =
      ZIO.service[ReadWriteLock[L]].flatMap(_.write(zio))
  }
}
