/*
 * Copyright 2018 Confluent Inc.
 *
 * Licensed under the Confluent Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.confluent.io/confluent-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.confluent.connect.jdbc.dialect;

import io.confluent.connect.jdbc.util.ColumnDefinition;
import java.io.IOException;
import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import javax.xml.bind.DatatypeConverter;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import io.confluent.connect.jdbc.util.ColumnId;
import io.confluent.connect.jdbc.util.QuoteMethod;
import io.confluent.connect.jdbc.util.TableDefinition;
import io.confluent.connect.jdbc.util.TableDefinitionBuilder;
import io.confluent.connect.jdbc.util.TableId;
import io.debezium.data.geometry.Geography;
import io.debezium.data.geometry.Geometry;
import io.debezium.data.geometry.Point;
import io.debezium.time.ZonedTime;
import io.debezium.time.ZonedTimestamp;
import org.apache.kafka.connect.data.Date;
import org.apache.kafka.connect.data.Decimal;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Schema.Type;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Time;
import org.apache.kafka.connect.data.Timestamp;
import org.junit.Test;

import java.sql.Connection;
import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.data.Time;
import org.apache.kafka.connect.data.Timestamp;
import org.junit.Test;

public class PostgreSqlDatabaseDialectTest extends BaseDialectTest<PostgreSqlDatabaseDialect> {

  // 'SRID=3187;POINT(174.9479 -36.7208)'::postgis.geometry
  private static final Struct GEOMETRY_VALUE =
          Geometry.createValue(Geometry.schema(),
                  DatatypeConverter.parseHexBinary(
                          "0101000020730C00001C7C613255DE6540787AA52C435C42C0"),
                  3187);
  // 'MULTILINESTRING((169.1321 -44.7032, 167.8974 -44.6414))'::postgis.geography
  private static final Struct GEOGRAPHY_VALUE =
          Geography.createValue(Geography.schema(),
                  DatatypeConverter.parseHexBinary(
                          "0105000020E610000001000000010200000002000000A779C7293A2465400B462575025A46C0C66D3480B7FC6440C3D32B65195246C0"),
                  4326);
  private static final Struct POINT_VALUE = Point.createValue(Point.builder().build(), 1, 1);

  @Override
  protected PostgreSqlDatabaseDialect createDialect() {
    return new PostgreSqlDatabaseDialect(sourceConfigWithUrl("jdbc:postgresql://something/"));
  }

  @Test
  public void shouldMapPrimitiveSchemaTypeToSqlTypes() {
    assertPrimitiveMapping(Type.INT8, "SMALLINT");
    assertPrimitiveMapping(Type.INT16, "SMALLINT");
    assertPrimitiveMapping(Type.INT32, "INT");
    assertPrimitiveMapping(Type.INT64, "BIGINT");
    assertPrimitiveMapping(Type.FLOAT32, "REAL");
    assertPrimitiveMapping(Type.FLOAT64, "DOUBLE PRECISION");
    assertPrimitiveMapping(Type.BOOLEAN, "BOOLEAN");
    assertPrimitiveMapping(Type.BYTES, "BYTEA");
    assertPrimitiveMapping(Type.STRING, "TEXT");
  }

  @Test
  public void shouldMapDecimalSchemaTypeToDecimalSqlType() {
    assertDecimalMapping(0, "DECIMAL");
    assertDecimalMapping(3, "DECIMAL");
    assertDecimalMapping(4, "DECIMAL");
    assertDecimalMapping(5, "DECIMAL");
  }

  @Test
  public void testCustomColumnConverters() {
    assertColumnConverter(Types.OTHER, PostgreSqlDatabaseDialect.JSON_TYPE_NAME, Schema.STRING_SCHEMA, String.class);
    assertColumnConverter(Types.OTHER, PostgreSqlDatabaseDialect.JSONB_TYPE_NAME, Schema.STRING_SCHEMA, String.class);
    assertColumnConverter(Types.OTHER, PostgreSqlDatabaseDialect.UUID_TYPE_NAME, Schema.STRING_SCHEMA, UUID.class);
  }

  @Test
  public void shouldMapDataTypesForAddingColumnToTable() {
    verifyDataTypeMapping("SMALLINT", Schema.INT8_SCHEMA);
    verifyDataTypeMapping("SMALLINT", Schema.INT16_SCHEMA);
    verifyDataTypeMapping("INT", Schema.INT32_SCHEMA);
    verifyDataTypeMapping("BIGINT", Schema.INT64_SCHEMA);
    verifyDataTypeMapping("REAL", Schema.FLOAT32_SCHEMA);
    verifyDataTypeMapping("DOUBLE PRECISION", Schema.FLOAT64_SCHEMA);
    verifyDataTypeMapping("BOOLEAN", Schema.BOOLEAN_SCHEMA);
    verifyDataTypeMapping("TEXT", Schema.STRING_SCHEMA);
    verifyDataTypeMapping("BYTEA", Schema.BYTES_SCHEMA);
    verifyDataTypeMapping("DECIMAL", Decimal.schema(0));
    verifyDataTypeMapping("DATE", Date.SCHEMA);
    verifyDataTypeMapping("TIME", Time.SCHEMA);
    verifyDataTypeMapping("TIMESTAMP", Timestamp.SCHEMA);
    verifyDataTypeMapping("TIMETZ", ZonedTime.schema());
    verifyDataTypeMapping("TIMESTAMPTZ", ZonedTimestamp.schema());
    verifyDataTypeMapping("GEOMETRY", Geometry.schema());
    // Geography is also derived from Geometry
    verifyDataTypeMapping("GEOMETRY", Geography.schema());
    verifyDataTypeMapping("GEOMETRY", Point.schema());
  }

  @Test
  public void shouldMapDateSchemaTypeToDateSqlType() {
    assertDateMapping("DATE");
  }

  @Test
  public void shouldMapTimeSchemaTypeToTimeSqlType() {
    assertTimeMapping("TIME");
  }

  @Test
  public void shouldMapTimestampSchemaTypeToTimestampSqlType() {
    assertTimestampMapping("TIMESTAMP");
  }

  @Test
  public void shouldMapGeometryTypeToPostGisTypes() {
    assertMapping("GEOMETRY", Geometry.schema());
    assertMapping("GEOMETRY", Geography.schema());
    assertMapping("GEOMETRY", Point.schema());
  }

  @Test
  public void shouldBuildCreateQueryStatement() {
    assertEquals(
        "CREATE TABLE \"myTable\" (\n"
        + "\"c1\" INT NOT NULL,\n"
        + "\"c2\" BIGINT NOT NULL,\n"
        + "\"c3\" TEXT NOT NULL,\n"
        + "\"c4\" TEXT NULL,\n"
        + "\"c5\" DATE DEFAULT '2001-03-15',\n"
        + "\"c6\" TIME DEFAULT '00:00:00.000',\n"
        + "\"c7\" TIMESTAMP DEFAULT '2001-03-15 00:00:00.000',\n"
        + "\"c8\" DECIMAL NULL,\n"
        + "\"c9\" BOOLEAN DEFAULT TRUE,\n"
        + "PRIMARY KEY(\"c1\"))",
        dialect.buildCreateTableStatement(tableId, sinkRecordFields)
    );

    quoteIdentfiiers = QuoteMethod.NEVER;
    dialect = createDialect();

    assertEquals(
        "CREATE TABLE myTable (\n"
        + "c1 INT NOT NULL,\n"
        + "c2 BIGINT NOT NULL,\n"
        + "c3 TEXT NOT NULL,\n"
        + "c4 TEXT NULL,\n"
        + "c5 DATE DEFAULT '2001-03-15',\n"
        + "c6 TIME DEFAULT '00:00:00.000',\n"
        + "c7 TIMESTAMP DEFAULT '2001-03-15 00:00:00.000',\n"
        + "c8 DECIMAL NULL,\n"
        + "c9 BOOLEAN DEFAULT TRUE,\n"
        + "PRIMARY KEY(c1))",
        dialect.buildCreateTableStatement(tableId, sinkRecordFields)
    );
  }

  @Test
  public void shouldBuildAlterTableStatement() {
    assertEquals(
        Arrays.asList(
            "ALTER TABLE \"myTable\" \n"
            + "ADD \"c1\" INT NOT NULL,\n"
            + "ADD \"c2\" BIGINT NOT NULL,\n"
            + "ADD \"c3\" TEXT NOT NULL,\n"
            + "ADD \"c4\" TEXT NULL,\n"
            + "ADD \"c5\" DATE DEFAULT '2001-03-15',\n"
            + "ADD \"c6\" TIME DEFAULT '00:00:00.000',\n"
            + "ADD \"c7\" TIMESTAMP DEFAULT '2001-03-15 00:00:00.000',\n"
            + "ADD \"c8\" DECIMAL NULL,\n"
            + "ADD \"c9\" BOOLEAN DEFAULT TRUE"
        ),
        dialect.buildAlterTable(tableId, sinkRecordFields)
    );

    quoteIdentfiiers = QuoteMethod.NEVER;
    dialect = createDialect();

    assertEquals(
        Arrays.asList(
            "ALTER TABLE myTable \n"
            + "ADD c1 INT NOT NULL,\n"
            + "ADD c2 BIGINT NOT NULL,\n"
            + "ADD c3 TEXT NOT NULL,\n"
            + "ADD c4 TEXT NULL,\n"
            + "ADD c5 DATE DEFAULT '2001-03-15',\n"
            + "ADD c6 TIME DEFAULT '00:00:00.000',\n"
            + "ADD c7 TIMESTAMP DEFAULT '2001-03-15 00:00:00.000',\n"
            + "ADD c8 DECIMAL NULL,\n"
            + "ADD c9 BOOLEAN DEFAULT TRUE"
        ),
        dialect.buildAlterTable(tableId, sinkRecordFields)
    );
  }

  @Test
  public void shouldBuildInsertStatement() {
    TableDefinitionBuilder builder = new TableDefinitionBuilder().withTable("myTable");
    builder.withColumn("id1").type("int", JDBCType.INTEGER, Integer.class);
    builder.withColumn("id2").type("int", JDBCType.INTEGER, Integer.class);
    builder.withColumn("columnA").type("varchar", JDBCType.VARCHAR, String.class);
    builder.withColumn("columnB").type("varchar", JDBCType.VARCHAR, String.class);
    builder.withColumn("columnC").type("varchar", JDBCType.VARCHAR, String.class);
    builder.withColumn("columnD").type("varchar", JDBCType.VARCHAR, String.class);
    TableDefinition tableDefn = builder.build();
    assertEquals(
        "INSERT INTO \"myTable\" (\"id1\",\"id2\",\"columnA\",\"columnB\"," +
        "\"columnC\",\"columnD\") VALUES (?,?,?,?,?,?)",
        dialect.buildInsertStatement(tableId, pkColumns, columnsAtoD, tableDefn)
    );

    quoteIdentfiiers = QuoteMethod.NEVER;
    dialect = createDialect();

    assertEquals(
        "INSERT INTO myTable (id1,id2,columnA,columnB," +
        "columnC,columnD) VALUES (?,?,?,?,?,?)",
        dialect.buildInsertStatement(tableId, pkColumns, columnsAtoD, tableDefn)
    );

    builder = new TableDefinitionBuilder().withTable("myTable");
    builder.withColumn("id1").type("int", JDBCType.INTEGER, Integer.class);
    builder.withColumn("id2").type("int", JDBCType.INTEGER, Integer.class);
    builder.withColumn("columnA").type("varchar", JDBCType.VARCHAR, Integer.class);
    builder.withColumn("uuidColumn").type("uuid", JDBCType.OTHER, UUID.class);
    builder.withColumn("dateColumn").type("date", JDBCType.DATE, java.sql.Date.class);
    tableDefn = builder.build();
    List<ColumnId> nonPkColumns = new ArrayList<>();
    nonPkColumns.add(new ColumnId(tableId, "columnA"));
    nonPkColumns.add(new ColumnId(tableId, "uuidColumn"));
    nonPkColumns.add(new ColumnId(tableId, "dateColumn"));
    assertEquals(
        "INSERT INTO myTable (" +
        "id1,id2,columnA,uuidColumn,dateColumn" +
        ") VALUES (?,?,?,?::uuid,?)",
        dialect.buildInsertStatement(tableId, pkColumns, nonPkColumns, tableDefn)
    );
  }
  @Test
  public void shouldBuildUpsertStatement() {
    TableDefinitionBuilder builder = new TableDefinitionBuilder().withTable("myTable");
    builder.withColumn("id1").type("int", JDBCType.INTEGER, Integer.class);
    builder.withColumn("id2").type("int", JDBCType.INTEGER, Integer.class);
    builder.withColumn("columnA").type("varchar", JDBCType.VARCHAR, String.class);
    builder.withColumn("columnB").type("varchar", JDBCType.VARCHAR, String.class);
    builder.withColumn("columnC").type("varchar", JDBCType.VARCHAR, String.class);
    builder.withColumn("columnD").type("varchar", JDBCType.VARCHAR, String.class);
    TableDefinition tableDefn = builder.build();
    assertEquals(
        "INSERT INTO \"myTable\" (\"id1\",\"id2\",\"columnA\",\"columnB\"," +
        "\"columnC\",\"columnD\") VALUES (?,?,?,?,?,?) ON CONFLICT (\"id1\"," +
        "\"id2\") DO UPDATE SET \"columnA\"=EXCLUDED" +
        ".\"columnA\",\"columnB\"=EXCLUDED.\"columnB\",\"columnC\"=EXCLUDED" +
        ".\"columnC\",\"columnD\"=EXCLUDED.\"columnD\"",
        dialect.buildUpsertQueryStatement(tableId, pkColumns, columnsAtoD, tableDefn)
    );

    quoteIdentfiiers = QuoteMethod.NEVER;
    dialect = createDialect();

    assertEquals(
        "INSERT INTO myTable (id1,id2,columnA,columnB," +
        "columnC,columnD) VALUES (?,?,?,?,?,?) ON CONFLICT (id1," +
        "id2) DO UPDATE SET columnA=EXCLUDED" +
        ".columnA,columnB=EXCLUDED.columnB,columnC=EXCLUDED" +
        ".columnC,columnD=EXCLUDED.columnD",
        dialect.buildUpsertQueryStatement(tableId, pkColumns, columnsAtoD, tableDefn)
    );

    builder = new TableDefinitionBuilder().withTable("myTable");
    builder.withColumn("id1").type("int", JDBCType.INTEGER, Integer.class);
    builder.withColumn("id2").type("int", JDBCType.INTEGER, Integer.class);
    builder.withColumn("columnA").type("varchar", JDBCType.VARCHAR, Integer.class);
    builder.withColumn("uuidColumn").type("uuid", JDBCType.OTHER, UUID.class);
    builder.withColumn("dateColumn").type("date", JDBCType.DATE, java.sql.Date.class);
    tableDefn = builder.build();
    List<ColumnId> nonPkColumns = new ArrayList<>();
    nonPkColumns.add(new ColumnId(tableId, "columnA"));
    nonPkColumns.add(new ColumnId(tableId, "uuidColumn"));
    nonPkColumns.add(new ColumnId(tableId, "dateColumn"));
    assertEquals(
        "INSERT INTO myTable (" +
        "id1,id2,columnA,uuidColumn,dateColumn" +
        ") VALUES (?,?,?,?::uuid,?) ON CONFLICT (id1," +
        "id2) DO UPDATE SET " +
        "columnA=EXCLUDED.columnA," +
        "uuidColumn=EXCLUDED.uuidColumn," +
        "dateColumn=EXCLUDED.dateColumn",
        dialect.buildUpsertQueryStatement(tableId, pkColumns, nonPkColumns, tableDefn)
    );
  }

  @Test
  public void shouldComputeValueTypeCast() {
    TableDefinitionBuilder builder = new TableDefinitionBuilder().withTable("myTable");
    builder.withColumn("id1").type("int", JDBCType.INTEGER, Integer.class);
    builder.withColumn("id2").type("int", JDBCType.INTEGER, Integer.class);
    builder.withColumn("columnA").type("varchar", JDBCType.VARCHAR, Integer.class);
    builder.withColumn("uuidColumn").type("uuid", JDBCType.OTHER, UUID.class);
    builder.withColumn("dateColumn").type("date", JDBCType.DATE, java.sql.Date.class);
    TableDefinition tableDefn = builder.build();
    ColumnId uuidColumn = tableDefn.definitionForColumn("uuidColumn").id();
    ColumnId dateColumn = tableDefn.definitionForColumn("dateColumn").id();
    assertEquals("", dialect.valueTypeCast(tableDefn, columnPK1));
    assertEquals("", dialect.valueTypeCast(tableDefn, columnPK2));
    assertEquals("", dialect.valueTypeCast(tableDefn, columnA));
    assertEquals("::uuid", dialect.valueTypeCast(tableDefn, uuidColumn));
    assertEquals("", dialect.valueTypeCast(tableDefn, dateColumn));
  }

  @Test
  public void createOneColNoPk() {
    verifyCreateOneColNoPk(
        "CREATE TABLE \"myTable\" (" + System.lineSeparator() + "\"col1\" INT NOT NULL)");
  }

  @Test
  public void createOneColOnePk() {
    verifyCreateOneColOnePk(
        "CREATE TABLE \"myTable\" (" + System.lineSeparator() + "\"pk1\" INT NOT NULL," +
        System.lineSeparator() + "PRIMARY KEY(\"pk1\"))");
  }

  @Test
  public void createThreeColTwoPk() {
    verifyCreateThreeColTwoPk(
        "CREATE TABLE \"myTable\" (" + System.lineSeparator() + "\"pk1\" INT NOT NULL," +
        System.lineSeparator() + "\"pk2\" INT NOT NULL," + System.lineSeparator() +
        "\"col1\" INT NOT NULL," + System.lineSeparator() + "PRIMARY KEY(\"pk1\",\"pk2\"))");

    quoteIdentfiiers = QuoteMethod.NEVER;
    dialect = createDialect();

    verifyCreateThreeColTwoPk(
        "CREATE TABLE myTable (" + System.lineSeparator() + "pk1 INT NOT NULL," +
        System.lineSeparator() + "pk2 INT NOT NULL," + System.lineSeparator() +
        "col1 INT NOT NULL," + System.lineSeparator() + "PRIMARY KEY(pk1,pk2))");
  }

  @Test
  public void alterAddOneCol() {
    verifyAlterAddOneCol("ALTER TABLE \"myTable\" ADD \"newcol1\" INT NULL");
  }

  @Test
  public void alterAddTwoCol() {
    verifyAlterAddTwoCols(
        "ALTER TABLE \"myTable\" " + System.lineSeparator() + "ADD \"newcol1\" INT NULL," +
        System.lineSeparator() + "ADD \"newcol2\" INT DEFAULT 42");
  }

  @Test
  public void upsert() {
    TableDefinitionBuilder builder = new TableDefinitionBuilder().withTable("Customer");
    builder.withColumn("id").type("int", JDBCType.INTEGER, Integer.class);
    builder.withColumn("name").type("varchar", JDBCType.VARCHAR, String.class);
    builder.withColumn("salary").type("real", JDBCType.FLOAT, String.class);
    builder.withColumn("address").type("varchar", JDBCType.VARCHAR, String.class);
    TableDefinition tableDefn = builder.build();
    TableId customer = tableDefn.id();
    assertEquals(
        "INSERT INTO \"Customer\" (\"id\",\"name\",\"salary\",\"address\") " +
         "VALUES (?,?,?,?) ON CONFLICT (\"id\") DO UPDATE SET \"name\"=EXCLUDED.\"name\"," +
         "\"salary\"=EXCLUDED.\"salary\",\"address\"=EXCLUDED.\"address\"",
        dialect.buildUpsertQueryStatement(
            customer,
            columns(customer, "id"),
            columns(customer, "name", "salary", "address"),
            tableDefn
        )
    );

    assertEquals(
            "INSERT INTO \"Customer\" (\"id\",\"name\",\"salary\",\"address\") " +
                    "VALUES (?,?,?,?) ON CONFLICT (\"id\",\"name\",\"salary\",\"address\") DO NOTHING",
            dialect.buildUpsertQueryStatement(
                    customer,
                    columns(customer, "id", "name", "salary", "address"),
                    columns(customer),
                    tableDefn
            )
    );

    quoteIdentfiiers = QuoteMethod.NEVER;
    dialect = createDialect();

    assertEquals(
        "INSERT INTO Customer (id,name,salary,address) " +
        "VALUES (?,?,?,?) ON CONFLICT (id) DO UPDATE SET name=EXCLUDED.name," +
        "salary=EXCLUDED.salary,address=EXCLUDED.address",
        dialect.buildUpsertQueryStatement(
            customer,
            columns(customer, "id"),
            columns(customer, "name", "salary", "address"),
            tableDefn
        )
    );

    assertEquals(
            "INSERT INTO Customer (id,name,salary,address) " +
                    "VALUES (?,?,?,?) ON CONFLICT (id,name,salary,address) DO NOTHING",
            dialect.buildUpsertQueryStatement(
                    customer,
                    columns(customer, "id", "name", "salary", "address"),
                    columns(customer),
                    tableDefn
            )
    );
  }

  @Test
  public void shouldSanitizeUrlWithoutCredentialsInProperties() {
    assertSanitizedUrl(
        "jdbc:postgresql://localhost/test?user=fred&ssl=true",
        "jdbc:postgresql://localhost/test?user=fred&ssl=true"
    );
  }

  @Test
  public void shouldSanitizeUrlWithCredentialsInUrlProperties() {
    assertSanitizedUrl(
        "jdbc:postgresql://localhost/test?user=fred&password=secret&ssl=true",
        "jdbc:postgresql://localhost/test?user=fred&password=****&ssl=true"
    );
  }

  @Test
  @Override
  public void bindFieldArrayUnsupported() throws SQLException {
      // Overridden simply to dummy out the test.
  }

  @Test
  public void bindFieldZonedTimeValue() throws SQLException {
    int index = ThreadLocalRandom.current().nextInt();
    String value = "10:15:30+01:00";
    super.verifyBindField(++index, ZonedTime.schema(), value).setObject(index, value, Types.OTHER);
  }

  @Test
  public void bindFieldZonedTimestampValue() throws SQLException {
    int index = ThreadLocalRandom.current().nextInt();
    String value = "2021-05-01T18:00:00.030431+02:00";
    super.verifyBindField(++index, ZonedTimestamp.schema(), value).setObject(index, value, Types.OTHER);
  }

  @Test
  public void bindFieldPostGisValues() throws SQLException, IOException {
    int index = ThreadLocalRandom.current().nextInt();
    super.verifyBindField(++index, Geometry.schema(), GEOMETRY_VALUE)
            .setBytes(index, GEOMETRY_VALUE.getBytes(Geometry.WKB_FIELD));
    super.verifyBindField(++index, Geography.schema(), GEOGRAPHY_VALUE)
            .setBytes(index, GEOGRAPHY_VALUE.getBytes(Geometry.WKB_FIELD));
    super.verifyBindField(++index, Geometry.schema(), POINT_VALUE)
            .setBytes(index, POINT_VALUE.getBytes(Geometry.WKB_FIELD));
  }

  @Test
  public void bindFieldPrimitiveValues() throws SQLException {
    PreparedStatement statement = mock(PreparedStatement.class);
    int index = ThreadLocalRandom.current().nextInt();

    super.verifyBindField(++index, SchemaBuilder.array(Schema.INT32_SCHEMA), Collections.singletonList(42)).setObject(index, new Object[] { 42 }, Types.ARRAY);
    super.verifyBindField(++index, SchemaBuilder.array(Schema.INT8_SCHEMA), Arrays.asList( (byte) 42, (byte) 12)).setObject(index, new Object[] { (short)42, (short)12 }, Types.ARRAY);
    super.verifyBindField(++index, SchemaBuilder.array(Schema.INT16_SCHEMA), Arrays.asList( (short) 42, (short) 12)).setObject(index, new Object[] { (short)42, (short)12 }, Types.ARRAY);
    super.verifyBindField(++index, SchemaBuilder.array(Schema.INT32_SCHEMA), Arrays.asList(42, 16 )).setObject(index, new Object[] { 42, 16 }, Types.ARRAY);
    super.verifyBindField(++index, SchemaBuilder.array(Schema.INT64_SCHEMA), Arrays.asList(42L, 16L )).setObject(index, new Object[] { (long)42, (long)16 }, Types.ARRAY);
    super.verifyBindField(++index, SchemaBuilder.array(Schema.FLOAT32_SCHEMA), Arrays.asList(42.5F, 16.2F )).setObject(index, new Object[] { 42.5F, 16.2F }, Types.ARRAY);
    super.verifyBindField(++index, SchemaBuilder.array(Schema.FLOAT64_SCHEMA), Arrays.asList(42.5D, 16.2D )).setObject(index, new Object[] { 42.5D, 16.2D }, Types.ARRAY);
    super.verifyBindField(++index, SchemaBuilder.array(Schema.STRING_SCHEMA), Arrays.asList("42", "16" )).setObject(index, new Object[] { "42", "16" }, Types.ARRAY);
    super.verifyBindField(++index, SchemaBuilder.array(Schema.BOOLEAN_SCHEMA), Arrays.asList(true, false, true )).setObject(index, new Object[] { true, false, true }, Types.ARRAY);
  }

  @Test
  public void shouldComputeMaxTableNameLength() throws Exception {
    int expectedMaxLength = 24;
    ResultSet resultSet = mock(ResultSet.class);
    when(resultSet.next()).thenReturn(true);
    when(resultSet.getInt(1)).thenReturn(expectedMaxLength);

    Statement statement = mock(Statement.class);
    when(statement.executeQuery("SELECT length(repeat('1234567890', 1000)::NAME);"))
        .thenReturn(resultSet);

    Connection connection = mock(Connection.class);
    when(connection.createStatement()).thenReturn(statement);

    int actualMaxLength = PostgreSqlDatabaseDialect.computeMaxIdentifierLength(connection);

    assertEquals(expectedMaxLength, actualMaxLength);
  }

  @Test
  public void shouldGracefullyHandleErrorWhenComputingMaxTableNameLength() throws Exception {
    Statement statement = mock(Statement.class);
    when(statement.executeQuery("SELECT length(repeat('1234567890', 1000)::NAME);"))
        .thenThrow(new SQLException("I plead the fifth"));

    Connection connection = mock(Connection.class);
    when(connection.createStatement()).thenReturn(statement);

    int actualMaxLength = PostgreSqlDatabaseDialect.computeMaxIdentifierLength(connection);

    assertEquals(Integer.MAX_VALUE, actualMaxLength);
  }

  @Test
  public void shouldGracefullyHandleEmptyResultSetWhenComputingMaxTableNameLength() throws Exception {
    ResultSet resultSet = mock(ResultSet.class);
    when(resultSet.next()).thenReturn(false);

    Statement statement = mock(Statement.class);
    when(statement.executeQuery("SELECT length(repeat('1234567890', 1000)::NAME);"))
        .thenReturn(resultSet);

    Connection connection = mock(Connection.class);
    when(connection.createStatement()).thenReturn(statement);

    int actualMaxLength = PostgreSqlDatabaseDialect.computeMaxIdentifierLength(connection);

    assertEquals(Integer.MAX_VALUE, actualMaxLength);
  }

  @Test
  public void shouldGracefullyHandleInvalidValueWhenComputingMaxTableNameLength() throws Exception {
    ResultSet resultSet = mock(ResultSet.class);
    when(resultSet.next()).thenReturn(true);
    when(resultSet.getInt(1)).thenReturn(0);

    Statement statement = mock(Statement.class);
    when(statement.executeQuery("SELECT length(repeat('1234567890', 1000)::NAME);"))
        .thenReturn(resultSet);

    Connection connection = mock(Connection.class);
    when(connection.createStatement()).thenReturn(statement);

    int actualMaxLength = PostgreSqlDatabaseDialect.computeMaxIdentifierLength(connection);

    assertEquals(Integer.MAX_VALUE, actualMaxLength);
  }

  @Test
  public void shouldTruncateTableNames() {

    final String tableFqn = "some.table";

    // Table name is one byte longer than it's allowed to be; should be truncated
    dialect.maxIdentifierLength = 4;
    TableId expectedTableId = new TableId(
        null,
        "some",
        "tabl"
    );
    TableId actualTableId = dialect.parseTableIdentifier(tableFqn);
    assertEquals(expectedTableId, actualTableId);

    // Table name is exactly as long as it's allowed to be; should not be truncated
    dialect.maxIdentifierLength = 5;
    expectedTableId = new TableId(
        null,
        "some",
        "table"
    );
    actualTableId = dialect.parseTableIdentifier(tableFqn);
    assertEquals(expectedTableId, actualTableId);

    // Something went wrong when computing the max length
    dialect.maxIdentifierLength = Integer.MAX_VALUE;
    expectedTableId = new TableId(
        null,
        "some",
        "table"
    );
    actualTableId = dialect.parseTableIdentifier(tableFqn);
    assertEquals(expectedTableId, actualTableId);

    // We haven't computed the max length at all yet
    dialect.maxIdentifierLength = 0;
    expectedTableId = new TableId(
        null,
        "some",
        "table"
    );
    actualTableId = dialect.parseTableIdentifier(tableFqn);
    assertEquals(expectedTableId, actualTableId);
  }

  @Test
  public void shouldFallBackOnUnknownDecimalScale() {
    ColumnId columnId = new ColumnId(new TableId("catalog", "schema", "table"), "column");
    ColumnDefinition definition = mock(ColumnDefinition.class);
    when(definition.id()).thenReturn(columnId);

    when(definition.precision()).thenReturn(4);
    when(definition.scale()).thenReturn(GenericDatabaseDialect.NUMERIC_TYPE_SCALE_UNSET);

    assertEquals(GenericDatabaseDialect.NUMERIC_TYPE_SCALE_HIGH, dialect.decimalScale(definition));
  }

  @Test
  public void shouldFallBackOnUnfixedDecimalScale() {
    ColumnId columnId = new ColumnId(new TableId("catalog", "schema", "table"), "column");
    ColumnDefinition definition = mock(ColumnDefinition.class);
    when(definition.id()).thenReturn(columnId);

    when(definition.precision()).thenReturn(0);
    when(definition.scale()).thenReturn(0);

    assertEquals(GenericDatabaseDialect.NUMERIC_TYPE_SCALE_HIGH, dialect.decimalScale(definition));
  }

  @Test
  public void shouldNotFallBackOnKnownDecimalScale() {
    ColumnId columnId = new ColumnId(new TableId("catalog", "schema", "table"), "column");
    ColumnDefinition definition = mock(ColumnDefinition.class);
    when(definition.id()).thenReturn(columnId);

    when(definition.precision()).thenReturn(0);
    when(definition.scale()).thenReturn(5);

    assertEquals(5, dialect.decimalScale(definition));
  }

}
