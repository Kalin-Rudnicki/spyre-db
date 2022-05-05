package spyreDb.db

import cats.syntax.option.*
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import klib.utils.*
import zio.*

// NOTE : This class is not thread safe, and needs to be used in the context of a ReadWriteLock
final class ByteFile private (val file: File, freeListHeadRef: Ref[Option[Long]], raf: RandomAccessFile) {

  private def setNext(prevAddr: Option[Long], addr: Option[Long]): TaskM[Unit] =
    prevAddr match {
      case Some(prevAddr) =>
        for {
          _ <- ZIOM.attempt(raf.seek(prevAddr + 5))
          _ <- ZIOM.attempt(raf.writeLong(addr.getOrElse(0L)))
        } yield ()
      case None =>
        for {
          _ <- freeListHeadRef.set(addr)
          _ <- ZIOM.attempt(raf.seek(0L))
          _ <- ZIOM.attempt(raf.writeLong(addr.getOrElse(0L)))
        } yield ()
    }

  def writeBytes(bytes: Array[Byte]): TaskM[Long] = {
    def writeToAddr(addr: Long): TaskM[Unit] =
      for {
        _ <- ZIOM.attempt(raf.seek(addr))
        // TODO (KR) : Int overflow?
        byteBuffer <- ZIOM.attempt(ByteBuffer.allocate(bytes.length.max(8) + 5))
        _ <- ZIOM.attempt(byteBuffer.put(1: Byte))
        _ <- ZIOM.attempt(byteBuffer.putInt(bytes.length))
        _ <- ZIOM.attempt(byteBuffer.put(bytes))
        _ <- ZIOM.attempt(raf.write(byteBuffer.array))
      } yield ()

    def loop(prevAddr: Option[Long], addr: Option[Long]): TaskM[Long] =
      addr match {
        case Some(addr) =>
          for {
            _ <- ZIOM.attempt(raf.seek(addr))
            byteBuffer <- ZIOM.attempt(ByteBuffer.allocate(13))
            _ <- ZIOM.attempt(raf.readFully(byteBuffer.array))
            byte <- ZIOM.attempt(byteBuffer.get)
            _ <- ZIO.unless(byte == (0: Byte))(ZIO.fail(KError.message.unexpected("You tried to read from an invalid free-block")))
            size <- ZIOM.attempt(byteBuffer.getInt)
            next <- ZIOM.attempt(byteBuffer.getLong).map(n => Option.when(n != 0L)(n))
            res <-
              if (bytes.length > size)
                loop(addr.some, next)
              else
                for {
                  _ <- writeToAddr(addr)
                  _ <- setNext(prevAddr, next)
                } yield addr
          } yield res
        case None =>
          for {
            eof <- ZIOM.attempt(raf.length)
            _ <- writeToAddr(eof)
          } yield eof
      }

    for {
      freeListHead <- freeListHeadRef.get
      addr <- loop(None, freeListHead)
    } yield addr
  }

  def writeString(string: String): TaskM[Long] =
    writeBytes(string.getBytes)

  def readBytes(addr: Long): TaskM[Array[Byte]] =
    for {
      _ <- ZIOM.attempt(raf.seek(addr))
      byteBuffer <- ZIOM.attempt(ByteBuffer.allocate(5))
      _ <- ZIOM.attempt(raf.readFully(byteBuffer.array))
      byte <- ZIOM.attempt(byteBuffer.get)
      _ <- ZIO.unless(byte == (1: Byte))(ZIO.fail(KError.message.unexpected("You tried to read from an invalid live-block")))
      size <- ZIOM.attempt(byteBuffer.getInt)
      bytes = new Array[Byte](size)
      _ <- ZIOM.attempt(raf.readFully(bytes))
    } yield bytes

  def readString(addr: Long): TaskM[String] =
    readBytes(addr).map(String(_))

  def free(addr: Long): TaskM[Unit] =
    for {
      _ <- ZIOM.attempt(raf.seek(addr))
      byteBuffer <- ZIOM.attempt(ByteBuffer.allocate(5))
      _ <- ZIOM.attempt(raf.readFully(byteBuffer.array))
      byte <- ZIOM.attempt(byteBuffer.get)
      _ <- ZIO.unless(byte == (1: Byte))(ZIO.fail(KError.message.unexpected("You tried to free an invalid live-block")))
      size <- ZIOM.attempt(byteBuffer.getInt)
      freeListHead <- freeListHeadRef.get
      byteBuffer <- ZIOM.attempt(ByteBuffer.allocate(13))
      _ <- ZIOM.attempt(byteBuffer.put(0: Byte))
      _ <- ZIOM.attempt(byteBuffer.putInt(size))
      _ <- ZIOM.attempt(byteBuffer.putLong(freeListHead.getOrElse(0L)))
      _ <- ZIOM.attempt(raf.seek(addr))
      _ <- ZIOM.attempt(raf.write(byteBuffer.array))
      _ <- setNext(None, addr.some)
    } yield ()

}
object ByteFile {

  def open(file: File, mode: String): RIOM[Scope, ByteFile] =
    for {
      javaFile <- ZIOM.attempt(file.toJavaFile)
      raf <- ZIO.acquireRelease(ZIOM.attempt(RandomAccessFile(javaFile, mode)))(raf => ZIOM.attempt(raf.close).orDieKlib)
      len <- ZIOM.attempt(raf.length)
      freeListHead <-
        if (len == 0) ZIOM.attempt(raf.writeLong(0L)).as(None)
        else ZIOM.attempt(raf.readLong).asSome
      freeListHeadRef <- Ref.make(freeListHead)
    } yield ByteFile(file, freeListHeadRef, raf)

}
