package com.recognai.rdf.spark

import com.recognai.rdf.spark.enrichment.RDFEnrichment
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{Dataset, SparkSession}

/**
  * Created by @frascuchon on 16/10/2016.
  */
object RDFProcessing {

  import operations._

  def apply(inputRDF: RDD[Triple])(implicit sparkSession: SparkSession): (Dataset[Subject], Dataset[Triple]) = apply(inputRDF, Seq.empty)

  def apply(inputRDF: RDD[Triple], enrichments: Seq[RDFEnrichment])(implicit sparkSession: SparkSession)
  : (Dataset[Subject], Dataset[Triple]) = {
    import sparkSession.implicits._

    val rdfDF = inputRDF.toDS().as("RDF")

    val enrichedRDF = enrichDataset(enrichments, rdfDF)

    val subjects = enrichedRDF
      .groupByKey(_.Subject)
      .mapGroups { (subject, groupData) => Subject(subject, createPropertiesMap(groupData)) }

    (subjects, enrichedRDF)
  }

  private def enrichDataset(enrichments: Seq[RDFEnrichment], rdfDF: Dataset[Triple])
                           (implicit sparkSession: SparkSession): Dataset[Triple] = {

    enrichments.foldLeft(rdfDF)((rdf, enrichment) => rdf.union(enrichment.enrichRDF(rdf)))
  }

  private def createPropertiesMap(group: Iterator[Triple])
  : Map[String, Seq[ObjectProperty]] = {

    def mergeProperties(map: Map[String, Seq[ObjectProperty]], triple: Triple)
    : Map[String, Seq[ObjectProperty]] = {

      val key = getURIName(triple.Predicate)
      val value = map.getOrElse(key, Seq.empty); map + (key -> value.+:(triple.Object))
    }

    group.foldLeft(Map.empty[String, Seq[ObjectProperty]])(mergeProperties)
  }
}
