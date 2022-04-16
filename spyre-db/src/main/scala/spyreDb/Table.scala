package spyreDb

import cats.data.NonEmptyList
import klib.utils.*

sealed trait Table {

  val tableName: String

}
object Table {

  final case class Standard(tableName: String, columns: List[Column[ColumnType.NonPK]]) extends Table
  final case class Polymorphic(tableName: String, sharedColumns: List[Column[ColumnType.NonPK]], subTypes: NENEList[Table]) extends Table

  def standard(tableName: String)(columns: Column[ColumnType.NonPK]*): Standard =
    Standard(tableName, columns.toList)
  def polymorphic(tableName: String)(sharedColumns: Column[ColumnType.NonPK]*)(t0: Table, t1: Table, tN: Table*): Polymorphic =
    Polymorphic(tableName, sharedColumns.toList, NENEList(t0, t1, tN.toList))

  private def allColumns(table: Table, rNamespace: List[String], cols: List[Column[ColumnType.NonPK]]): NonEmptyList[Column[ColumnType]] = {
    val path = NonEmptyList(table.tableName, rNamespace).reverse
    val pk = Column.primaryKey(path.head, path.tail*)
    table match {
      case _: Table.Standard    => NonEmptyList(pk, Column.byte("polymorphicId") :: cols)
      case _: Table.Polymorphic => NonEmptyList(pk, cols)
    }
  }

  def showHierarchy(table: Table): String = {
    def showColumns(table: Table, rNamespace: List[String], cols: List[Column[ColumnType.NonPK]]): String =
      allColumns(table, rNamespace, cols).toList.zipWithIndex.map { (c, i) => s"[$i] ${c.columnName}: ${c.columnType.typeName}" }.mkString("(", ", ", ")")

    def rec(table: Table, rNamespace: List[String], inheritedColumns: List[Column[ColumnType.NonPK]]): IndentedString =
      table match {
        case Standard(tableName, columns) =>
          s"$tableName${showColumns(table, rNamespace, inheritedColumns)}"
        case Polymorphic(tableName, sharedColumns, subTypes) =>
          IndentedString.`inline`(
            s"*$tableName${showColumns(table, rNamespace, inheritedColumns)} ->",
            IndentedString.indented(
              subTypes.toList.map(rec(_, tableName :: rNamespace, inheritedColumns ::: sharedColumns)),
            ),
          )
      }

    IndentedString
      .`inline`(
        "--- Hierarchy ---",
        rec(table, Nil, Nil),
      )
      .toString("    ")
  }

}
