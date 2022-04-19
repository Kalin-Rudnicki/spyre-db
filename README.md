
# SpyreDb

### Problem

- What is this idea?  
  Playing around wiith the idea of a different way to model a database.
- Why?  
  It really seems to me that every time I need to interact with a database, it is always a negative experience, and I want to try and contemplate whether it could be done better.
- What causes this negative experience?  
  I see 3 primary reasons:  
  1) Needing to make sacrifices on data model accuracy/integrety, because standard databases don't support any concept of polymorphism, or ADTs
  2) Even simple/common queries seem very inefficient to me...  
     I have never seen the code for a database, but I imagine a join looking something like this:
     ```scala
     tableA.foreach { a =>
       join(tableB, on).foreach { b =>
         output(a._1, a._2, a._3, b._1, b._2, b._3)
       }
     }
     ```
     Just so that the person sending the query can immediately do this:
     ```scala
     results.groupMap(_.a)(_.b)
     ```
  3) Because of these frustrations, as well as a lack of an ORM that I have really fallen in love with, it is just so un-fun to work with databases. I see that as a really bad thing, given that a significant majority of non-trivial programs use a database.

### Solution (?)

- Modeling return types:
  ```scala
  // --- 1 ---

  // normal db
  List[(
    A1, A2, A3,
    B1, B2, B3,
  )]

  // spyre
  List[(
    A1, A2, A3,
    List[(
      B1, B2, B3,
    )],
  )]

  // --- 2 ---

  // normal db
  List[(
    A1, A2, A3,
    B1, B2, B3,
    C1, C2, C3,
  )]

  // spyre
  List[(
    A1, A2, A3,
    List[(
      B1, B2, B3,
      List[(
        C1, C2, C3,
      )],
    )],
  )]

  // --- 3 ---

  // normal db
  // Im not sure if there is even a way to do this with a single query...

  // spyre
  List[(
    A1, A2, A3,
    List[(
      B1, B2, B3,
    )],
    List[(
      C1, C2, C3,
    )],
  )]
  ```
- Type safety surrounding foreign keys:  
  If you have a foreign key in `Song` that references `Album`, it is a `ForeignKey[Album]`. All PKs & FKs are UUIDs (at least for now, for simplicity).
- Polyorphism:  
  I believe it should be possible to support polymorphic tables, as well as polymorphic columns. More examples on this soon. Going along with the previous point of typing foreign keys, it should be possible to have a foreign key that references a sub-type of a particular table.
- "Joins", or their moral equivalent, should be much more obvious, and not require any sort of `ON` clause, as it knows which type the foreign key is, and which table (or sub-set of that table) to look for.  
- Note to self : Is there also a good way to encode `1:1` vs `many:1`?
- Schema definition:  
  I believe this one still needs some thinking, but here is what Ive got so far. SQL supports this ability to accept any sort of query any time. For example, you could just randomy send a `CREATE TABLE` / `DROP TABLE` / `ALTER TABLE` query at any time, even while the database is running normally. Personally, I think this is a bad thing, and I can not think of a single practical usage for sending one of these types of queries, except when you are initially creating the db, or doing a migration.  
  That being said, I think the process should go like this:  
  1) Define a schema
  2) Run some tool on the schema definition to codegen a type-safe database that conforms to that schema
  3) When you want to change the schema, you create a migration, which defines the changes to the database
  4) You run the same tool on the migration, and it re-gens a new database that also knows how to migrate from the previous schema  
     (Also possibly knows how to support queries from previous schemas?)
  5) Since the db knows everything about what it is supposed to be, and there is no support for this on-the-fly schema alteration, it shoud be possible to also code-gen libraries to easily define queries and interact with the database.  
  6) This could be done for several frameworks/languages: `lib-scala-zio`, `lib-scala-cats`, `lib-ruby`, `lib-python`, ...  
     Then, if someone contributes the ability to code-gen for a specific language/framework, all databases would then support the ability to have an automatic library to use with that language.  
     It would also be possible to (either in the same lib, or a separate one) define a lib for the same language/framework, but possibly a different DSL or preference on how to define queries.
  7) It should then be possible to hit the db socket with a request for a lib, and it will stream/download it for you. Maybe this doesnt really end up having any practical use in the end, but its an idea none the less, lol.  
  It probably makes more sense to have the codegen tool be able to publish to `sonatype`/`gem`/...


### Output

```
=====| MusicalEntity |=====
--- Hierarchy ---
*MusicalEntity([0] id: PrimaryKey[MusicalEntity], [1] polymorphicId: Byte) ->
    Musician([0] id: PrimaryKey[MusicalEntity.Musician], [1] polymorphicId: Byte, [2] firstName: String, [3] lastName: String, [4] birthday: Long)
    Band([0] id: PrimaryKey[MusicalEntity.Band], [1] polymorphicId: Byte, [2] name: String, [3] formationDate: Long)
--- Byte Layout ---
MusicalEntity.Musician : ?00000000000000001222222223333333344444444
    MusicalEntity.Band : ?000000000000000012222222233333333________


=====| MusicianInBand |=====
--- Hierarchy ---
MusicianInBand([0] id: PrimaryKey[MusicianInBand], [1] musicianId: ForeignKey[MusicalEntity.Musician], [2] bandId: ForeignKey[MusicalEntity.Band], [3] instrument: String)
--- Byte Layout ---
MusicianInBand : ?00000000000000001111111111111111222222222222222233333333


=====| Album |=====
--- Hierarchy ---
Album([0] id: PrimaryKey[Album], [1] name: String, [2] madeById: ForeignKey[MusicalEntity])
--- Byte Layout ---
Album : ?0000000000000000111111112222222222222222


=====| Song |=====
--- Hierarchy ---
Song([0] id: PrimaryKey[Song], [1] name: String, [2] belongsToId: Polymorphic[ForeignKey[MusicalEntity] | ForeignKey[Album]])
--- Byte Layout ---
Song : ?00000000000000001111111122222222222222222
```

```
=====| Vehicle |=====
--- Hierarchy ---
*Vehicle([0] id: PrimaryKey[Vehicle], [1] polymorphicId: Byte) ->
    Boat([0] id: PrimaryKey[Vehicle.Boat], [1] polymorphicId: Byte, [2] color: String, [3] length: Double)
    *LandVehicle([0] id: PrimaryKey[Vehicle.LandVehicle], [1] polymorphicId: Byte, [2] color: String) ->
        RoadVehicle([0] id: PrimaryKey[Vehicle.LandVehicle.RoadVehicle], [1] polymorphicId: Byte, [2] color: String, [3] numWheels: Int, [4] licensePlateNo: String)
        OffRoadVehicle([0] id: PrimaryKey[Vehicle.LandVehicle.OffRoadVehicle], [1] polymorphicId: Byte, [2] color: String, [3] numWheels: Int, [4] canDriveInMud: Boolean)
--- Byte Layout ---
                      Vehicle.Boat : ?000000000000000012222222233333333____
   Vehicle.LandVehicle.RoadVehicle : ?0000000000000000122222222333344444444
Vehicle.LandVehicle.OffRoadVehicle : ?000000000000000012222222233334_______
```

### Example Schema

Note that this is very much just a draft.  
Also, this can be found in `spyreDb.main.Main.scala`, if you would like to play around with it.  
It doesnt do anything remotely intelligent at this point, it just prints the shema out.

```scala
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
    Column.polymorphic("belongsToId")(
      ColumnType.foreignKey("MusicalEntity"),
      ColumnType.foreignKey("Album"),
    ),
  ),
)
```

Here are a few special things to note:
  - `MusicalEntity` is a polymorphic table.  
    This lets you represent that it can either be a `MusicalEntity.Band` or a `MusicalEntity.Musician`
  - `MusicianInBand` is able to have foreign keys that reference `MusicalEntity.Band` and `MusicalEntity.Musician` directly, as opposed to the table as a whole
  - `Album` is able to have a foreign key that references `MusicalEntity` as a whole.  
    This lets you represent that an `Album` is either produced by a `Band`, or an individual `Musician`.
  - `Song` has a polymorphic column that could either point to a `MusicalEntity` or an `Album`.  
    This lets you represent that a `Song` is either a single or part of an `Album`.  
    If it is a single, it could belong to either a `Band` or a `Musician`.

### Example Query

I really need to put some more thought into this, but I imagine defining a query would look something like this:

```scala
  val query1: Query[
    List[(
      MusicalEntity.Musician,
      List[Song],
      List[(
        Album,
        List[Song]
      )],
    )],
  ] =
    MusicalEntity.Musician.select { m =>
      (
        m,
        Song.belongsToId(m.id),
        Album.madeById(m.id).select { a =>
          (
            a,
            Song.belongsToId(a.id),
          )
        },
      )
    }
```

```scala
  val query2: Query[
    List[(
      MusicalEntity.Band,
      List[(
        String,
        MusicalEntity.Musician,
      )],
    )],
  ] =
    MusicalEntity.Band.select { b =>
      (
        b,
        MusicianInBand.bandId(b.id).select { mib =>
          (
            mib.instrument,
            Musician.id(mib.musicianId),
          )
        },
      )
    }
```
