package spyreDb

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

}
