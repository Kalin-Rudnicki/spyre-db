package spyreDb

import cats.data.NonEmptyList

final case class Column[+C <: ColumnType](columnName: String, columnType: C) {
  def optional(implicit ev: C <:< ColumnType.NonOptional): Column[ColumnType.Optional] = Column(columnName, ColumnType.Optional(ev(columnType)))
}
object Column {
  import ColumnType.*

  // polymorphic
  def polymorphic(columnName: String)(st0: Standard, st1: Standard, stN: Standard*): Column[Polymorphic] = Column(columnName, Polymorphic(NENEList(st0, st1, stN.toList)))

  // uuid
  def primaryKey(r0: String, rN: String*): Column[PrimaryKey] = Column("id", PrimaryKey(NonEmptyList(r0, rN.toList)))
  def foreignKey(columnName: String)(r0: String, rN: String*): Column[ForeignKey] = Column(columnName, ForeignKey(NonEmptyList(r0, rN.toList)))
  def uuid(columnName: String): Column[UUID.type] = Column(columnName, UUID)

  // integer
  def byte(columnName: String): Column[_Byte.type] = Column(columnName, _Byte)
  def short(columnName: String): Column[_Short.type] = Column(columnName, _Short)
  def int(columnName: String): Column[_Int.type] = Column(columnName, _Int)
  def long(columnName: String): Column[_Long.type] = Column(columnName, _Long)

  // decimal
  def float(columnName: String): Column[_Float.type] = Column(columnName, _Float)
  def double(columnName: String): Column[_Double.type] = Column(columnName, _Double)

  // other
  def boolean(columnName: String): Column[_Boolean.type] = Column(columnName, _Boolean)
  def string(columnName: String): Column[_String.type] = Column(columnName, _String)

}
