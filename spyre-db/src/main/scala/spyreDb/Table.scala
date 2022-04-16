package spyreDb

import cats.data.NonEmptyList

sealed trait Table {

  val tableName: String

  private final def allColumns(rNamespace: List[String], cols: List[Column[ColumnType.NonPK]]): NonEmptyList[Column[ColumnType]] =
    this match {
      case _: Table.Standard    => NonEmptyList(Column.primaryKey(tableName, rNamespace.reverse*), Column.byte("polymorphicId") :: cols)
      case _: Table.Polymorphic => NonEmptyList(Column.primaryKey(tableName, rNamespace.reverse*), cols)
    }

}
object Table {

  final case class Standard(tableName: String, columns: List[Column[ColumnType.NonPK]]) extends Table
  final case class Polymorphic(tableName: String, sharedColumns: List[Column[ColumnType.NonPK]], subTypes: NENEList[Table]) extends Table

  def standard(tableName: String)(columns: Column[ColumnType.NonPK]*): Standard =
    Standard(tableName, columns.toList)
  def polymorphic(tableName: String)(sharedColumns: Column[ColumnType.NonPK]*)(t0: Table, t1: Table, tN: Table*): Polymorphic =
    Polymorphic(tableName, sharedColumns.toList, NENEList(t0, t1, tN.toList))

}
