package spyreDb.db

import java.io.RandomAccessFile

final class VariableSizeStorage(vssRAF: RandomAccessFile) {

  def free(pos: Long): Unit = ???

  private def alloc(numBytesNotIncludingMetaData: Int): Long = ???

  def writeString(string: String): Long = ???

  def readString(pos: Long): String = ???

  // Good or bad idea?
  def writeStringIfDifferent(pos: Long, string: String): Long = ???

}
