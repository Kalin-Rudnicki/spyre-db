package spyreDb.schema

import cats.data.NonEmptyList

final case class NENEList[+T](`0`: T, `1`: T, tail: List[T]) {
  def toNel: NonEmptyList[T] = NonEmptyList(`0`, `1` :: tail)
  def toList: List[T] = `0` :: `1` :: tail
}
