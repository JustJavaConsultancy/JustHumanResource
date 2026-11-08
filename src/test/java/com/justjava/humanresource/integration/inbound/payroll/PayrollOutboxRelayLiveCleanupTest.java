package com.justjava.humanresource.integration.inbound.payroll;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justjava.humanresource.HumanResourceApplication;
import com.justjava.humanresource.integration.inbound.payroll.repository.PayrollJournalSnapshotRepository;
import com.justjava.humanresource.payroll.repositories.PayrollJournalBatchRepository;
import com.justjava.humanresource.payroll.repositories.PayrollJournalEntryRepository;
import com.justjava.humanresource.payroll.repositories.PayrollPeriodRepository;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "live.rabbit", matches = "true")
@SpringBootTest(
        classes = HumanResourceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "app.scheduling.enabled=false",
                "integration.payroll-accounting.enabled=true",
                "integration.payroll-accounting.company-id=970000000000",
                "logging.level.root=OFF",
                "logging.level.org.hibernate.orm.connections.pooling=OFF"
        })
@ActiveProfiles("proof-local")
@ContextConfiguration(initializers = com.justjava.humanresource.payroll.workflow.PayrollProofDatabaseGuard.class)
class PayrollOutboxRelayLiveCleanupTest {
    private static final String LIVE_QUEUE_PREFIX =
            "integration.payroll.accounting.livetest.justhr-";

    @Autowired private RabbitProperties rabbitProperties;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PayrollJournalSnapshotRepository snapshots;
    @Autowired private PayrollJournalEntryRepository entries;
    @Autowired private PayrollJournalBatchRepository batches;
    @Autowired private PayrollPeriodRepository periods;
    @Autowired private RuntimeService runtimeService;
    @Autowired private HistoryService historyService;

    @Test
    @EnabledIfSystemProperty(named = "live.rabbit.cleanup", matches = "true")
    void removesOnlyTheLiveTestQueueBindingAndHandoffJournal() throws Exception {
        String reference = System.getProperty("live.reference");
        assertThat(reference).isNotBlank();
        ManagementApi api = managementApi();
        JsonNode queues = api.get("/api/queues");
        List<JsonNode> candidates = new ArrayList<>();
        for (JsonNode queue : queues) {
            String name = queue.path("name").asText();
            if (name.matches("integration\\.payroll\\.accounting\\.livetest\\.justhr-[0-9a-fA-F-]{36}")
                    && queue.path("messages").asInt() == 1
                    && queue.path("consumers").asInt() == 0) {
                candidates.add(queue);
            }
        }
        assertThat(candidates)
                .as("one isolated live-test queue contains the retained handoff event")
                .hasSize(1);
        String queueName = candidates.getFirst().path("name").asText();

        JsonNode bindings = api.get("/api/bindings/" + pathSegment(rabbitProperties.getVirtualHost()));
        List<JsonNode> ownedBindings = new ArrayList<>();
        for (JsonNode binding : bindings) {
            if (binding.path("source").asText().equals("justjava.business.events")
                    && binding.path("destination").asText().equals(queueName)
                    && binding.path("destination_type").asText().equals("queue")
                    && binding.path("routing_key").asText().equals("justhr.payroll.finalised")) {
                ownedBindings.add(binding);
            }
        }
        assertThat(ownedBindings).hasSize(1);
        api.delete("/api/bindings/" + pathSegment(rabbitProperties.getVirtualHost())
                + "/e/" + pathSegment("justjava.business.events")
                + "/q/" + pathSegment(queueName)
                + "/" + pathSegment("justhr.payroll.finalised"));
        api.delete("/api/queues/" + pathSegment(rabbitProperties.getVirtualHost())
                + "/" + pathSegment(queueName));
        assertThat(api.queueExists(queueName)).isFalse();
        System.out.println("LIVE_CLEANUP queue=" + queueName + " binding=deleted");

        List<Map<String, Object>> rows = jdbc.queryForList(
                "select payroll_period_id, company_id from payroll_journal_snapshot where journal_reference=?",
                reference);
        assertThat(rows).hasSize(1);
        Long periodId = ((Number) rows.getFirst().get("payroll_period_id")).longValue();
        Long companyId = ((Number) rows.getFirst().get("company_id")).longValue();

        Set<String> processIds = new HashSet<>();
        runtimeService.createProcessInstanceQuery()
                .variableValueEquals("periodId", periodId)
                .list()
                .forEach(instance -> {
                    processIds.add(instance.getId());
                    runtimeService.deleteProcessInstance(instance.getId(), "Stage 3 live handoff cleanup");
                });
        historyService.createHistoricProcessInstanceQuery()
                .variableValueEquals("periodId", periodId)
                .list()
                .forEach(instance -> processIds.add(instance.getId()));
        String nextPeriodBusinessKey = "BATCH_PERIOD_" + periodId;
        runtimeService.createProcessInstanceQuery()
                .processInstanceBusinessKey(nextPeriodBusinessKey)
                .list()
                .forEach(instance -> {
                    processIds.add(instance.getId());
                    runtimeService.deleteProcessInstance(instance.getId(), "Stage 3 live handoff cleanup");
                });
        historyService.createHistoricProcessInstanceQuery()
                .processInstanceBusinessKey(nextPeriodBusinessKey)
                .list()
                .forEach(instance -> processIds.add(instance.getId()));
        for (String processId : processIds) {
            if (historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(processId).singleResult() != null) {
                historyService.deleteHistoricProcessInstance(processId);
            }
        }

        jdbc.update("""
                delete from payroll_journal_snapshot_line
                where snapshot_id in (select id from payroll_journal_snapshot where payroll_period_id=?)
                """, periodId);
        jdbc.update("delete from integration_outbox where aggregate_id=?", reference);
        jdbc.update("delete from integration_alert where payroll_period_id=?", periodId);
        jdbc.update("delete from payroll_journal_snapshot where payroll_period_id=?", periodId);
        entries.deleteAll(entries.findByCompanyIdAndPayrollPeriodId(companyId, periodId));
        batches.findByCompanyIdAndPayrollPeriodId(companyId, periodId).ifPresent(batches::delete);
        periods.deleteAll(periods.findByCompanyIdOrderByPeriodStartDesc(companyId));
        assertThat(snapshots.findByJournalReferenceAndStatusNot(
                reference, com.justjava.humanresource.integration.inbound.payroll.enums.SnapshotStatus.VOID))
                .isEmpty();
        assertThat(jdbc.queryForObject(
                "select count(*) from integration_outbox where aggregate_id=?", Integer.class, reference))
                .isZero();
        System.out.println("LIVE_CLEANUP reference=" + reference + " snapshot=deleted outbox=deleted "
                + "payrollPeriod=deleted flowableProcessRecords=" + processIds.size());
    }

    private ManagementApi managementApi() {
        String userPassword = rabbitProperties.getUsername() + ":" + rabbitProperties.getPassword();
        String authorization = "Basic " + Base64.getEncoder()
                .encodeToString(userPassword.getBytes(StandardCharsets.UTF_8));
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        List<URI> candidates = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        String host = rabbitProperties.getHost();
        candidates.add(URI.create("https://" + host + "/api/queues"));
        candidates.add(URI.create("https://" + host + ":15671/api/queues"));
        int lastStatus = -1;
        for (URI queuesUri : candidates) {
            try {
                HttpRequest request = HttpRequest.newBuilder(queuesUri)
                        .timeout(Duration.ofSeconds(15))
                        .header("Authorization", authorization)
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    String root = queuesUri.toString().substring(
                            0, queuesUri.toString().length() - "/api/queues".length());
                    return new ManagementApi(client, URI.create(root), authorization);
                }
                lastStatus = response.statusCode();
            } catch (Exception failure) {
                failures.add(failure.getClass().getSimpleName());
            }
        }
        client.close();
        throw new IllegalStateException(
                "Could not access the broker's TLS management API; no queues or bindings were changed. "
                        + "Failure types=" + String.join(",", failures) + "; last HTTP status=" + lastStatus);
    }

    private String pathSegment(String value) {
        return URLEncoder.encode(value == null ? "/" : value, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private static final class ManagementApi {
        private final HttpClient client;
        private final URI root;
        private final String authorization;

        private ManagementApi(HttpClient client, URI root, String authorization) {
            this.client = client;
            this.root = root;
            this.authorization = authorization;
        }

        private JsonNode get(String path) throws Exception {
            HttpResponse<String> response = send(path, "GET");
            assertThat(response.statusCode()).isEqualTo(200);
            return new ObjectMapper().readTree(response.body());
        }

        private void delete(String path) throws Exception {
            HttpResponse<String> response = send(path, "DELETE");
            assertThat(response.statusCode()).isIn(204, 200);
        }

        private boolean queueExists(String queueName) throws Exception {
            for (JsonNode queue : get("/api/queues")) {
                if (queueName.equals(queue.path("name").asText())) {
                    return true;
                }
            }
            return false;
        }

        private HttpResponse<String> send(String path, String method) throws Exception {
            HttpRequest.Builder request = HttpRequest.newBuilder(root.resolve(path))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", authorization);
            if ("DELETE".equals(method)) {
                request.DELETE();
            } else {
                request.GET();
            }
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
