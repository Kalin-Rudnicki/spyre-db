package spyreDb.main

import cats.data.NonEmptyList
import klib.utils.{given, *}

import spyreDb.*

object Main {

  def main(args: Array[String]): Unit = {
    def showTable(table: Table): Unit = {
      println
      println
      println(s"=====| ${table.tableName} |=====")
      println(Table.showHierarchy(table))
      println(Table.byteLayout(table))
    }

    def showSchema(name: String, show: Boolean)(tables: Table*): Unit =
      if (show) {
        println
        println(s"==========| ${name.red} |==========")
        tables.foreach(showTable)
        println
      }

    showSchema("schema-1", true)(
      Table.standard("Person")(
        Column.string("firstName"),
        Column.string("lastName"),
        Column.long("birthday").optional,
      ),
      Table.polymorphic("Vehicle")(
        Column.string("color"),
      )(
        Table.standard("Boat")(
          Column.double("length"),
        ),
        Table.polymorphic("LandVehicle")(
          Column.int("numWheels"),
        )(
          Table.standard("RoadVehicle")(
            Column.string("licensePlateNo"),
          ),
          Table.standard("OffRoadVehicle")(
            Column.boolean("canDriveInMud"),
          ),
        ),
      ),
      Table.standard("PersonOwnsVehicle")(
        Column.foreignKey("personId")("Person"),
        Column.foreignKey("vehicleId")("Vehicle"),
      ),
      Table.standard("TrafficViolationStandard")(
        Column.foreignKey("officerPersonId")("Person"),
        Column.foreignKey("vehicleId")("Vehicle", "LandVehicle", "RoadVehicle"),
        Column.double("fineAmount"),
        Column.foreignKey("driverPersonId")("Person").optional,
      ),
      Table.polymorphic("TrafficViolationPolymorphic")(
        Column.foreignKey("officerPersonId")("Person"),
        Column.foreignKey("vehicleId")("Vehicle", "LandVehicle", "RoadVehicle"),
        Column.double("fineAmount"),
      )(
        Table.standard("NonMovingViolation")(),
        Table.standard("MovingViolation")(
          Column.foreignKey("driverPersonId")("Person"),
        ),
      ),
    )

    showSchema("schema-2", true)(
      Table.polymorphic("MusicalEntity")(
      )(
        Table.standard("Musician")(
          Column.string("firstName"),
          Column.string("lastName"),
          Column.long("birthday"),
        ),
        Table.standard("Band")(
          Column.string("name"),
          Column.long("formationDate"),
        ),
      ),
      Table.standard("MusicianInBand")(
        Column.foreignKey("musicianId")("MusicalEntity", "Musician"),
        Column.foreignKey("bandId")("MusicalEntity", "Band"),
        Column.string("instrument"),
      ),
      Table.standard("Album")(
        Column.string("name"),
        Column.foreignKey("madeById")("MusicalEntity"),
      ),
      Table.standard("Song")(
        Column.string("name"),
        Column.polymorphic("belongsTo")(
          ColumnType.foreignKey("MusicalEntity"),
          ColumnType.foreignKey("Album"),
        ),
      ),
    )
  }

}
