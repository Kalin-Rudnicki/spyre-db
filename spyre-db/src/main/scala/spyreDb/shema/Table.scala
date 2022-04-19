package spyreDb.schema

import cats.data.NonEmptyList
import klib.utils.*
import scala.annotation.internal.sharable

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

  private def allColumns(table: Table, poly: Boolean, rNamespace: List[String], cols: List[Column[ColumnType.NonPK]]): NonEmptyList[Column[ColumnType]] = {
    val path = NonEmptyList(table.tableName, rNamespace).reverse
    val pk = Column.primaryKey(path.head, path.tail*)
    NonEmptyList(pk, if (poly) Column.byte("polymorphicId") :: cols else cols)
  }

  private def isPoly(table: Table): Boolean =
    table match {
      case _: Table.Standard    => false
      case _: Table.Polymorphic => true
    }

  def showHierarchy(table: Table): String = {
    val poly = isPoly(table)

    def showColumns(table: Table, rNamespace: List[String], cols: List[Column[ColumnType.NonPK]]): String =
      allColumns(table, poly, rNamespace, cols).toList.zipWithIndex.map { (c, i) => s"[$i] ${c.columnName}: ${c.columnType.typeName}" }.mkString("(", ", ", ")")

    def rec(table: Table, rNamespace: List[String], inheritedColumns: List[Column[ColumnType.NonPK]]): IndentedString =
      table match {
        case Standard(tableName, columns) =>
          s"$tableName${showColumns(table, rNamespace, inheritedColumns ::: columns)}"
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

  def byteLayout(table: Table): String = {
    val poly = isPoly(table)

    def col(c: Column[ColumnType], i: Int): String = {
      val char: Char =
        if (i < 0) ???
        else if (i < 10) (i + '0').toChar
        else if (i < 36) (i + 'A' - 10).toChar
        else if (i < 62) (i + 'a' - 36).toChar
        else ???

      char.toString * c.columnType.numBytes
    }

    def rec(table: Table, rNamespace: List[String], inheritedColumns: List[Column[ColumnType.NonPK]]): NonEmptyList[(String, String)] =
      table match {
        case Standard(tableName, columns) =>
          NonEmptyList.one(
            (
              NonEmptyList(tableName, rNamespace).toList.reverse.mkString("."),
              allColumns(table, poly, rNamespace, inheritedColumns ::: columns).toList.zipWithIndex.map(col).mkString("?", "", ""),
            ),
          )
        case Polymorphic(tableName, sharedColumns, subTypes) =>
          subTypes.toNel.flatMap(rec(_, tableName :: rNamespace, inheritedColumns ::: sharedColumns))
      }

    val res = rec(table, Nil, Nil)
    val maxNameLength = res.map(_._1.length).toList.max
    val maxBytes = res.map(_._2.length).toList.max

    res
      .map { (n, b) => s"${n.alignRight(maxNameLength, ' ')} : ${b.alignLeft(maxBytes, '_')}" }
      .toList
      .mkString("--- Byte Layout ---\n", "\n", "")
  }

}
