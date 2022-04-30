package spyreDb.db

import java.util.UUID
import klib.utils.*
import zio.*
import zio.stream.*

object Tmp {

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
