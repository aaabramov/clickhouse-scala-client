package com.crobox.clickhouse.dsl.execution

import akka.stream.scaladsl.Source
import com.crobox.clickhouse.ClickhouseClient
import com.crobox.clickhouse.dsl.language.{ClickhouseTokenizerModule, TokenizerModule}
import com.crobox.clickhouse.dsl.{Query, Table}
import com.crobox.clickhouse.internal.QuerySettings
import com.crobox.clickhouse.internal.progress.QueryProgress.QueryProgress
import spray.json.{JsonReader, _}

import scala.concurrent.{ExecutionContext, Future}

trait ClickhouseQueryExecutor extends QueryExecutor { self: TokenizerModule =>
  implicit val client: ClickhouseClient

  def execute[V: JsonReader](query: Query)(implicit executionContext: ExecutionContext,
                                           settings: QuerySettings = QuerySettings()): Future[QueryResult[V]] = {
    import QueryResult._
    val queryResult = client.query(toSql(query.internalQuery)(client.database))
    queryResult.map(_.parseJson.convertTo[QueryResult[V]])
  }

  def executeWithProgress[V: JsonReader](
      query: Query
  )(implicit executionContext: ExecutionContext,
    settings: QuerySettings = QuerySettings()): Source[QueryProgress, Future[QueryResult[V]]] = {
    import QueryResult._
    val queryResult = client.queryWithProgress(toSql(query.internalQuery)(client.database))
    queryResult.mapMaterializedValue(_.map(_.parseJson.convertTo[QueryResult[V]]))
  }

  override def insert[V: JsonWriter](
      table: Table,
      values: Seq[V]
  )(implicit executionContext: ExecutionContext, settings: QuerySettings = QuerySettings()): Future[String] =
    Future {
      values.map(_.toJson.compactPrint).mkString("\n") + "\n"
    }.flatMap(
      entity => client.execute(s"INSERT INTO ${client.table(table.name)} FORMAT JSONEachRow", entity)
    )

}

object ClickhouseQueryExecutor {

  def default(clickhouseCLient: ClickhouseClient): ClickhouseQueryExecutor =
    new DefaultClickhouseQueryExecutor(clickhouseCLient)
}

class DefaultClickhouseQueryExecutor(override val client: ClickhouseClient)
    extends ClickhouseQueryExecutor
    with ClickhouseTokenizerModule
