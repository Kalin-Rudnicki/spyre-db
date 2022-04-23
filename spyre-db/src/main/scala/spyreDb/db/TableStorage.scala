package spyreDb.db

import java.io.RandomAccessFile
import java.util.UUID
import klib.utils.*
import zio.*
import zio.stream.*

final class TableStorage[R](rowCodec: RowCodec[R], tableRAF: RandomAccessFile, vssRAF: RandomAccessFile) {

  private val vss = VariableSizeStorage(vssRAF)

  def insert(row: R): TaskM[Unit] = ZIO.fail(KError.message.???)

  def delete(id: UUID): TaskM[Unit] = ZIO.fail(KError.message.???)

  def all: TaskStreamM[R] = ZStream.fail(KError.message.???)

}
