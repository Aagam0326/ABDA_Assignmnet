# Restaurant Discovery & Analytics System

A menu-driven Scala CLI application for MongoDB's official `sample_restaurants.restaurants` sample collection.

## Requirements

- JDK 17
- Scala CLI
- MongoDB local server or MongoDB Atlas access
- The MongoDB sample dataset loaded into database `sample_restaurants`, collection `restaurants`

The build directives in `project.scala` select Scala 2.13.14, JDK 17, and the official MongoDB Scala driver 5.1.0.

## Load the sample dataset

For Atlas, open the cluster's **Load Sample Dataset** action and load MongoDB's sample data. Confirm that `sample_restaurants` and its `restaurants` collection are present. For local MongoDB, follow MongoDB's sample dataset import instructions and load the same sample database.

## Configure MongoDB

The app reads the connection string from `MONGO_URI`. If it is unset, it connects to `mongodb://localhost:27017`.

PowerShell (current terminal only):

```powershell
$env:MONGO_URI = "mongodb+srv://<user>:<password>@<cluster-host>/"
```

Replace the placeholders with your Atlas connection details. Do not put credentials in source files.

## Run

From the project root:

```powershell
scala-cli run .
```

On first run Scala CLI downloads the declared dependencies. The app creates the required indexes before displaying the menu. Select **7** to exit and close the MongoDB client.

## Menu and features

1. Add a restaurant with an empty `grades` array.
2. Search by case-insensitive name, cuisine plus borough, ZIP code, or minimum grade score.
3. Update `name`, `cuisine`, `borough`, or `zipcode` by `restaurant_id`.
4. Delete by `restaurant_id`.
5. View cuisine counts, borough counts, average grade score by cuisine (at least 100 grades), or grade distribution.
6. List indexes, create ascending or descending single-field indexes, create compound indexes in Equality → Sort → Range order, or drop an index by name. The required `_id_` index is protected.
7. Exit.

The four startup indexes are `name`, `address.zipcode`, `restaurant_id`, and the compound `borough` + `cuisine` index. Both fields in the compound startup index are equality fields. Additional indexes can be created or dropped from the index menu.
