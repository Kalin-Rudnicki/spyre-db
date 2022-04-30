package spyreDb.db

import java.util.UUID
import klib.utils.*
import zio.*
import zio.stream.*

object Tmp {

  trait ReadWriteLock[L: Tag] {
    def read[R, E, A](zio: ZIO[R & ReadWriteLock.ReadAccess[L], E, A]): ZIO[R, E, A]
    def write[R, E, A](zio: ZIO[R & ReadWriteLock.WriteAccess[L] & ReadWriteLock.ReadAccess[L], E, A]): ZIO[R, E, A]
  }
  object ReadWriteLock {
    final case class ReadAccess[L](accessId: UUID)
    final case class WriteAccess[L](accessId: UUID)

    def apply[L: Tag]: Applied[L] = new Applied[L]

    final class Applied[L: Tag] private[ReadWriteLock] {
      def read[R, E, A](zio: ZIO[R & ReadAccess[L], E, A]): ZIO[R & ReadWriteLock[L], E, A] =
        ZIO.service[ReadWriteLock[L]].flatMap(_.read(zio))
      def write[R, E, A](zio: ZIO[R & WriteAccess[L] & ReadAccess[L], E, A]): ZIO[R & ReadWriteLock[L], E, A] =
        ZIO.service[ReadWriteLock[L]].flatMap(_.write(zio))
    }
  }

  // Storages

  trait Page[L, Row] {
    def rows: RIOM[ReadWriteLock.ReadAccess[L], Chunk[Row]]
    def write(offsetInPage: Int, row: Row): RIOM[ReadWriteLock.WriteAccess[L], Unit]
  }
  trait Table[L, Row] {
    def page(pageNo: Long): RIOM[ReadWriteLock.ReadAccess[L], Page[L, Row]]
    def pages: RStreamM[ReadWriteLock.ReadAccess[L], Page[L, Row]]
  }
  trait SingleIndex[L] {
    def get(uuid: UUID): RIOM[ReadWriteLock.ReadAccess[L], Option[Long]]
    def update(uuid: UUID, addr: Option[Long]): RIOM[ReadWriteLock.WriteAccess[L], Unit]

    final def apply(uuid: UUID): RIOM[ReadWriteLock.ReadAccess[L], Long] = get(uuid).someOrFail(KError.message.same(s"Missing expected id: $uuid"))
  }
  trait MultiIndex[L] {
    def apply(uuid: UUID): ZStream[ReadWriteLock.ReadAccess[L], Option[KError[Nothing]], Long]
  }

  // TransactionViews

  trait PageView[Row] {}

}
