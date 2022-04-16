package spyreDb

import cats.data.NonEmptyList

sealed abstract class ColumnType(final val typeName: String, final val numBytes: Int)
object ColumnType {

  // non-pk
  sealed abstract class NonPK(typeName: String, numBytes: Int) extends ColumnType(typeName, numBytes)
  sealed abstract class NonOptional(typeName: String, final val numBytesNonOptional: Int, final val canRepresentNull: Boolean) extends NonPK(typeName, numBytesNonOptional)
  sealed abstract class Standard(typeName: String, numBytesNonOptional: Int, canRepresentNull: Boolean) extends NonOptional(typeName, numBytesNonOptional, canRepresentNull)

  // optional
  final case class Optional[+C <: NonOptional](base: C) extends NonPK(s"Option[${base.typeName}]", base.numBytesNonOptional + (if (base.canRepresentNull) 0 else 1))

  // polymorphic
  final case class Polymorphic(subTypes: NENEList[ColumnType.Standard])
      extends NonOptional(
        s"Polymorphic[${subTypes.toList.map(_.typeName).mkString(" | ")}]",
        subTypes.toList.map(_.numBytesNonOptional).max + 1,
        true,
      )

  // uuid
  final case class PrimaryKey(referencePath: NonEmptyList[String]) extends ColumnType(s"PrimaryKey[${referencePath.toList.mkString(".")}]", 16)
  final case class ForeignKey(referencePath: NonEmptyList[String]) extends Standard(s"ForeignKey[${referencePath.toList.mkString(".")}]", 16, false)
  case object UUID extends Standard("UUID", 16, false)

  // integer
  case object _Byte extends Standard("Byte", 1, false)
  case object _Short extends Standard("Short", 2, false)
  case object _Int extends Standard("Int", 4, false)
  case object _Long extends Standard("Long", 8, false)

  // decimal
  case object _Float extends Standard("Float", 4, false)
  case object _Double extends Standard("Double", 8, false)

  // other
  case object _Boolean extends Standard("Boolean", 1, true)
  case object _String extends Standard("String", 8, true)

}
