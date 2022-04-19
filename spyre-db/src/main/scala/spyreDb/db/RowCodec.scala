package spyreDb.db

import java.nio.ByteBuffer
import java.util.UUID
import zio.{Unzippable, Zippable}

final class RowCodec[R](
    private val _bytesPerRow: Int,
    private val _write: (VariableSizeStorage, ByteBuffer, R) => Unit,
    private val _read: (VariableSizeStorage, ByteBuffer) => R,
) { self =>

  val bytesPerRow: Int = _bytesPerRow.max(8)

  def append[R2](implicit
      other: RowCodec[R2],
      zip: Zippable[R, R2],
      unzip: Unzippable[R, R2],
      ev: zip.Out =:= unzip.In,
  ): RowCodec[zip.Out] =
    RowCodec[zip.Out](
      self._bytesPerRow + other._bytesPerRow,
      { (vss, byteBuffer, row) =>
        val (selfRow, otherRow) = unzip.unzip(row)
        self._write(vss, byteBuffer, selfRow)
        other._write(vss, byteBuffer, otherRow)
      },
      { (vss, byteBuffer) =>
        val selfRow = self._read(vss, byteBuffer)
        val otherRow = other._read(vss, byteBuffer)
        zip.zip(selfRow, otherRow)
      },
    )

  def some(vss: VariableSizeStorage, row: R): ByteBuffer = {
    val byteBuffer = ByteBuffer.allocate(bytesPerRow + 1)
    byteBuffer.put(1: Byte)
    self._write(vss, byteBuffer, row)
    byteBuffer
  }

  def none(nextEmptyRow: Long): ByteBuffer = {
    val byteBuffer = ByteBuffer.allocate(bytesPerRow + 1)
    byteBuffer.put(0: Byte)
    byteBuffer.putLong(nextEmptyRow)
    0.until(bytesPerRow - 8).foreach { _ => byteBuffer.put(0: Byte) }
    byteBuffer
  }

  def readOption(vss: VariableSizeStorage, byteBuffer: ByteBuffer): Option[R] =
    byteBuffer.get match {
      case 1 =>
        Some(self._read(vss, byteBuffer))
      case 0 =>
        0.until(bytesPerRow).foreach { _ => byteBuffer.get }
        None
      case _ =>
        ???
    }

}
object RowCodec {

  val empty: RowCodec[Unit] =
    RowCodec[Unit](
      0,
      (_, _, _) => (),
      (_, _) => (),
    )

  // =====| Implicits |=====

  inline final def withoutVSS[R](
      bytesPerRow: Int,
      write: (ByteBuffer, R) => Unit,
      read: (ByteBuffer) => R,
  ): RowCodec[R] =
    ???

  // --- Non-Optional ---

  implicit val uuidRowCodec: RowCodec[UUID] =
    RowCodec.withoutVSS[UUID](
      16,
      { (byteBuffer, row) =>
        byteBuffer.putLong(row.getMostSignificantBits)
        byteBuffer.putLong(row.getLeastSignificantBits)
      },
      { byteBuffer =>
        val msb = byteBuffer.getLong
        val lsb = byteBuffer.getLong
        UUID(msb, lsb)
      },
    )

  implicit val byteRowCodec: RowCodec[Byte] = RowCodec.withoutVSS[Byte](1, _.put(_), _.get)
  implicit val shortRowCodec: RowCodec[Short] = RowCodec.withoutVSS[Short](2, _.putShort(_), _.getShort)
  implicit val intRowCodec: RowCodec[Int] = RowCodec.withoutVSS[Int](4, _.putInt(_), _.getInt)
  implicit val longRowCodec: RowCodec[Long] = RowCodec.withoutVSS[Long](8, _.putLong(_), _.getLong)

  implicit val floatRowCodec: RowCodec[Float] = RowCodec.withoutVSS[Float](4, _.putFloat(_), _.getFloat)
  implicit val doubleRowCodec: RowCodec[Double] = RowCodec.withoutVSS[Double](8, _.putDouble(_), _.getDouble)

  implicit val booleanRowCodec: RowCodec[Boolean] =
    RowCodec.withoutVSS[Boolean](
      1,
      { (byteBuffer, row) =>
        row match {
          case true  => byteBuffer.put(-1: Byte)
          case false => byteBuffer.put(1: Byte)
        }
      },
      _.get match {
        case -1 => false
        case 1  => true
        case _  => ???
      },
    )

  implicit val stringRowCodec: RowCodec[String] =
    RowCodec[String](
      8,
      { (vss, byteBuffer, row) =>
        val pos = vss.writeString(row)
        byteBuffer.putLong(pos)
      },
      { (vss, byteBuffer) =>
        val pos = byteBuffer.getLong
        vss.readString(pos)
      },
    )

  // --- Optional ---

  inline private def withLeadingNullByte[R](writeIfNull: => R)(implicit nonNullable: RowCodec[R]): RowCodec[Option[R]] =
    RowCodec[Option[R]](
      nonNullable._bytesPerRow + 1,
      { (vss, byteBuffer, row) =>
        row match {
          case Some(row) =>
            byteBuffer.put(1: Byte)
            nonNullable._write(vss, byteBuffer, row)
          case None =>
            byteBuffer.put(0: Byte)
            nonNullable._write(vss, byteBuffer, writeIfNull)
        }
      },
      { (vss, byteBuffer) =>
        byteBuffer.get match {
          case 1 =>
            Some(nonNullable._read(vss, byteBuffer))
          case 0 =>
            nonNullable._read(vss, byteBuffer)
            None
          case _ =>
            ???
        }
      },
    )

  implicit val optionalUUIDRowCodec: RowCodec[Option[UUID]] = withLeadingNullByte[UUID](UUID(0L, 0L))

  implicit val optionalByteRowCodec: RowCodec[Option[Byte]] = withLeadingNullByte[Byte](0)
  implicit val optionalShortRowCodec: RowCodec[Option[Short]] = withLeadingNullByte[Short](0)
  implicit val optionalIntRowCodec: RowCodec[Option[Int]] = withLeadingNullByte[Int](0)
  implicit val optionalLongRowCodec: RowCodec[Option[Long]] = withLeadingNullByte[Long](0)

  implicit val optionalFloatRowCodec: RowCodec[Option[Float]] = withLeadingNullByte[Float](0)
  implicit val optionalDoubleRowCodec: RowCodec[Option[Double]] = withLeadingNullByte[Double](0)

  implicit val optionalBooleanRowCodec: RowCodec[Option[Boolean]] =
    RowCodec.withoutVSS[Option[Boolean]](
      1,
      { (byteBuffer, row) =>
        row match {
          case Some(true)  => byteBuffer.put(1: Byte)
          case Some(false) => byteBuffer.put(-1: Byte)
          case None        => byteBuffer.put(0: Byte)
        }
      },
      _.get match {
        case -1 => Some(false)
        case 1  => Some(true)
        case 0  => None
        case _  => ???
      },
    )

  implicit val optionalStringRowCodec: RowCodec[Option[String]] =
    RowCodec[Option[String]](
      8,
      { (vss, byteBuffer, row) =>
        row match {
          case Some(row) =>
            val pos = vss.writeString(row)
            byteBuffer.putLong(pos)
          case None =>
            byteBuffer.putLong(0L)
        }
      },
      { (vss, byteBuffer) =>
        val pos = byteBuffer.getLong
        if (pos == 0L) None
        else Some(vss.readString(pos))
      },
    )

}
