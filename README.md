
# SpyreDb

- What is this idea?  
  Playing around wiith the idea of a different way to model a database.
- Why?  
  It really seems to me that every time I need to interact with a database, it is always a negative experience, and I want to try and contemplate whether it could be done better.
- What causes this negative experience?  
  I see 3 primary reasons:  
  1) Needing to make sacrifices on data model accuracy/integrety, because standard databases don't support any concept of polymorphism, or ADTs. Maybe in some case it is possibe to do so through very hacky means, but this almost always feels bad/wrong.
  2) Even simple/common queries seem very inefficient to me...  
     I have never seen the code for a database, but I imagine a join looking something like this:
     ```scala
     tableA.foreach { a =>
       join(tableB, on).foreach { b =>
         output(a._1, a._2, a._3, b._1, b._2, b._3)
       }
     }
     ```
     The inefficient part of this being that if the join to `tableB` produces 100,000 rows for some `a`, you have outputted `a` 100,000 times. Maybe the database actually sends it 100,000 times over the wire, or maybe it only sends the first one, and then it gets added to every row, but either way its going to be in the memory of whoever sent the query 100,000 times.  
     Then, whoever sent the query probably immediately wants to do this:
     ```scala
     results.groupMap(_.a)(_.b)
     ```
     Furthermore, imagine you also joined this to some table `c`, which has 100 records per `(a, b)`. Now you've duplicated every `b` 100 times, and `a` 10,000,000 times.  
     The point here is that this way of querying data and returning the result is more of a widely accepted limitation of "just the way things are", as opposed to how we would actually like to query and return things (at least in my opinion).
  3) Because of these frustrations, as well as a lack of an ORM that I have really fallen in love with, it is just so un-fun to work with databases. I see that as a really bad thing, given that a significant majority of non-trivial programs use a database.  
    Maybe if there are a plethora of ORMs, and nobody can decide on which one is the least sufferable, then maybe the ORMs are not the problem, and the database model itself is :thinking:.

- As a side note relating to NOSQL databases: I am by no means super familiar with these, but after doing some research, the popular ones do not seem to solve the problems I want to solve without introducing new annoyances or problems.  
  I think that there are some flaws surrounding the standard SQL database, but I still think that the concept of tables, columns, and joins is the best way to go. It just needs to be tweaked.

### What should a database be/provide?

- The way the schema is modeled should feel like a natural extension of the problem you are trying to solve. At no point while designing your schema should you grimmace and say to yourself "oh... that feels hacky" or "ugh, I wish I didn't have to do it this way".
- While modeling your schema should feel natural and easy, you also shouldn't have to make sacrifices in data integrety, allowing for non-existent states to be stored in the database, just because the database doesn't have the ability to model it properly, or because you would have to fly over the moon in order to model it properly. This means we will need some level of polymorphism.
- While you should be able to model data as accurately as possible leveraging polymorphism, it should also be as simple to model as possible. It is very common that a database tables primary-key is a long or uuid, and then other tables reference it using that id. Lets just go ahead and say this is the only way to do it (uuid, it is automatically added as `id: UUID`, and does not need to be specified), and then lets also make those ids typed, so that it becomes blantantly obvious in your model what you are trying to achieve.
- Who uses databases? Database admins? No. Programmers. Databases should be designed in order to optimize for maximum efficiency for use in code, because that is what is going to be doing the most interaction with it.
  - Queries should be easy and obvious to write in code (I am personally a huge stickler for wanting an elegant DSL for writing something such as queries)
  - These queries should also be type-safe (assuming you are using a typed language)
  - While I want a nice DSL to define queries, the queries should actually be able to return what I want, not some backwards-ass representation that most people just accept as the fact of reality, then using an ORM to put a band-aid on it, and having to do silly transforms such as the `groupMap` mentioned above
- It should be able to do all of these things, while still being very efficient.  
  To be honest, I think that some of these changes could actually result in something more efficient that standard databases, given it goes through just as much optimization.
- A proof of concept should be considered a success if it is able return results as described below in a sensical way, with the opportunity to optimize it further. Optimization and a nice API to interact with it can always be added.

### Solution

- Modeling return types:
  ```scala
  // --- 1-deep join ---

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

  // --- 2-deep join ---

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

  // --- 2 1-deep joins ---

  // normal db
  // Im not sure if there is even a way to do this in a remotely sensical way with a single query...

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

  // --- left join ---
  
  // normal db
  List[(
    A1, A2, A3,
    Option[B1], Option[B2], Option[B3],
  )]

  // spyre
  List[(
    A1, A2, A3,
    Option[(B1, B2, B3)],
  )]

  // Heres a fun exercise: write a function that converts from the normal-db type into the spyre type, 
  //   and tell me how good you feel about that sitting around in your codebase.


  // --- left join (it gets worse) ---
  
  // normal db
  List[(
    A1, A2, A3,
    Option[B1], Option[B2], Option[B3],
  )]

  // spyre
  List[(
    A1, A2, A3,
    Option[(B1, B2, Option[B3])],
  )]

  // Notice how the way the normal-db type encodes the result totally loses information about
  //   which of the 2 spyre types it is meant to represent?
  ```
- Type safety surrounding foreign keys:  
  If you have a foreign key in `Song` that references `Album`, it is a `ForeignKey[Album]`. All PKs & FKs are UUIDs.
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
  It probably makes more sense to have the codegen tool be able to publish to `sonatype`/`gem`/`whatever-python-uses`/...

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

### More Thoughts

- As mentioned before, data integrety is one of the greatest concerns with this approach, and should therefore take precedence over something like concurrency. If a cleaver solution arises that can efficiently do both, then thats great. For the initial proof of concept, the entire database will be locked on writes, but multiple concurrent reads would be fine.
- Also on the note of data integrety, and relating to schema definition:  
  It should also be possible/required (?) to define the behavior of deletes & cascading. The options I can think of off the top of my head are:
    - Non-Optional Column : `Block`, `Cascade`
    - Optional Column : `Block`, `Cascade`, `Unset`

  With this information, the codegen tool should be able to generate clear documentation in plain language that says something along the lines of `If you delete a row from this table, then this is what will happen in these other tables: ...`
- I think one of the trickiest things to implement would be transactions, especially juggling them with the above constraint of data integrety being the most important thing. The logic that follows of making the dumbest, but most accurate implementation would be that you would have the lock the entire database for the entire duration of the write transaction. I am sure there is a way to make sensible tradeoffs here, but I am not sure what that would be yet.
