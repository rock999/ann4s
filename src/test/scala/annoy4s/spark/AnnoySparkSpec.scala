package annoy4s.spark

import annoy4s.Random
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{Row, DataFrame, SQLContext}
import org.scalatest.{FlatSpec, Matchers}

class AnnoySparkSpec extends FlatSpec with Matchers with LocalSparkContext {

  import annoy4s.profiling.AnnoyDataset.{dataset => features, trueNns}

  object FixRandom extends Random {
    val rnd = new scala.util.Random(0)
    override def flip(): Boolean = rnd.nextBoolean()
    override def index(n: Int): Int = rnd.nextInt(n)
  }

  "Spark ML API" should "work" in {
    val sqlContext = new SQLContext(sc)
    import sqlContext.implicits._

    val idCol = "id"
    val featuresCol = "features"
    val outputCol = "output"
    val dimension = features.head.length

    val rdd: RDD[(Int, Array[Float])] =
      sc.parallelize(features.zipWithIndex.map(_.swap))

    val dataset: DataFrame = rdd.toDF(idCol, featuresCol)

    val annoyModel: AnnoyModel = new Annoy()
      .setDimension(dimension)
      .setIdCol(idCol)
      .setFeaturesCol(featuresCol)
      .setOutputCol(outputCol)
      .setDebug(true)
      .fit(dataset)

    val result: DataFrame = annoyModel
      .setK(10) // find 10 neighbors
      .transform(dataset)

    result.show()

    result.select(idCol, outputCol).collect()
      .foreach { case Row(id: Int, output: Seq[_]) =>
        output.asInstanceOf[Seq[Int]]
          .intersect(trueNns(id)).length should be >= 2
      }
  }

}

