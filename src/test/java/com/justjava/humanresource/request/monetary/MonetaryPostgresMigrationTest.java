package com.justjava.humanresource.request.monetary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.sql.*;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;
@EnabledIfSystemProperty(named="monetary.postgres.url",matches=".+")
class MonetaryPostgresMigrationTest {
 static void requireDisposableDatabase(String url){if(!url.matches("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]+/request_enhancement_test"))throw new IllegalArgumentException("Only the dedicated local request_enhancement_test database is allowed.");}
 @Test void migrationIsRepeatablePreservesExistingDataAndEnforcesReferences() throws Exception {
  String url=System.getProperty("monetary.postgres.url");requireDisposableDatabase(url);
  try(var c=DriverManager.getConnection(url,"request_enhancement_test","");var s=c.createStatement()){
   s.execute("CREATE SCHEMA monetary_migration_test");s.execute("SET search_path TO monetary_migration_test");
   try {
    s.execute("CREATE TABLE workflow_request_types(id bigint PRIMARY KEY,code varchar(40)); INSERT INTO workflow_request_types VALUES(1,'EXPENSE_REIMBURSEMENT')");
    s.execute("CREATE TABLE workflow_requests(id bigint PRIMARY KEY,request_type varchar(40) CHECK(request_type IN ('STAFF_REQUISITION','FILE_REQUEST','ASSET_REQUEST','EXPENSE_REIMBURSEMENT','GENERAL_REQUEST'))); INSERT INTO workflow_requests VALUES(1,'EXPENSE_REIMBURSEMENT')");
    s.execute("CREATE TABLE grouped_approval_versions(id bigint PRIMARY KEY)");
    String sql=Files.readString(Path.of("db/migrations/20261103_request_monetary_enhancements.sql"));s.execute(sql);s.execute(sql);
    try(var rs=s.executeQuery("SELECT monetary_routing_enabled FROM workflow_request_types WHERE id=1")){assertThat(rs.next()).isTrue();assertThat(rs.getBoolean(1)).isFalse();}
    s.execute("INSERT INTO workflow_requests VALUES(2,'CASH_ADVANCE'),(3,'FUND_REQUEST')");
    try(var rs=s.executeQuery("SELECT count(*) FROM workflow_requests")){rs.next();assertThat(rs.getInt(1)).isEqualTo(3);}
    assertThatThrownBy(()->s.execute("INSERT INTO request_monetary_bindings(created_at,updated_at,request_type,currency,policy_id) VALUES(now(),now(),'FUND_REQUEST','NGN',999)")).isInstanceOf(SQLException.class);
   } finally {s.execute("ROLLBACK");s.execute("DROP SCHEMA monetary_migration_test CASCADE");}
  }
 }
}
