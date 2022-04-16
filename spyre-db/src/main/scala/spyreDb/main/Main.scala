package spyreDb.main

import klib.utils.{given, *}

import spyreDb.*

object Main {

  def main(args: Array[String]): Unit = {
    /*
    def showTable(table: Table): Unit = {
      println
      println
      println(s"=====| ${table.tableName} |=====")
      println("--- Fields ---")
      println(table.nestedColumns)
      println
      println("--- Byte Layout ---")
      println(table.byteLayout)
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
        Column.key("personId", "Person"),
        Column.key("vehicleId", "Vehicle"),
      ),
      Table.standard("TrafficViolationStandard")(
        Column.key("officerPersonId", "Person"),
        Column.key("vehicleId", "Vehicle", "LandVehicle", "RoadVehicle"),
        Column.double("fineAmount"),
        Column.key("driverPersonId", "Person").optional,
      ),
      Table.polymorphic("TrafficViolationPolymorphic")(
        Column.key("officerPersonId", "Person"),
        Column.key("vehicleId", "Vehicle", "LandVehicle", "RoadVehicle"),
        Column.double("fineAmount"),
      )(
        Table.standard("NonMovingViolation")(),
        Table.standard("MovingViolation")(
          Column.key("driverPersonId", "Person"),
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
        Column.key("musicianId", "MusicalEntity", "Musician"),
        Column.key("bandId", "MusicalEntity", "Band"),
      ),
      Table.standard("Album")(
        Column.string("name"),
        Column.key("madeById", "MusicalEntity"),
      ),
      Table.standard("Song")(
        Column.string("name"),
        Column.polymorphic("belongsTo")(
          ColumnType.KeyColumnType(List("MusicalEntity")),
          ColumnType.KeyColumnType(List("Album")),
        ),
      ),
    )
     */
  }

}
