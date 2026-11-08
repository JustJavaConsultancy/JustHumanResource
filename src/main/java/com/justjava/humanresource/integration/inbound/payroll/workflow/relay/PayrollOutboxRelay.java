package com.justjava.humanresource.integration.inbound.payroll.workflow.relay;

import com.justjava.humanresource.integration.inbound.payroll.config.IntegrationPayrollProperties;
import com.justjava.humanresource.integration.inbound.payroll.service.outbox.OutboxClaimService;
import com.justjava.humanresource.integration.inbound.payroll.service.outbox.OutboxResultService;

import com.justjava.humanresource.integration.inbound.payroll.dto.ClaimedPayrollEvent;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;




@Component
@ConditionalOnProperty(name = "integration.payroll-accounting.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class PayrollOutboxRelay {
    private final OutboxClaimService claims;
    private final OutboxResultService results;
    private final RabbitTemplate rabbitTemplate;
    private final IntegrationPayrollProperties properties;

    @Scheduled(fixedDelayString = "${integration.payroll-accounting.relay-interval:5000}")
    public void publishDueEvents() {
        for (ClaimedPayrollEvent claim : claims.claimDue()) {
            publish(claim);
        }
    }

    private void publish(ClaimedPayrollEvent claim) {
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        messageProperties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        messageProperties.setMessageId(claim.eventId().toString());
        messageProperties.setCorrelationId(claim.correlationId());
        CorrelationData correlationData = new CorrelationData(claim.eventId().toString());
        try {
            rabbitTemplate.send(
                    claim.exchange(),
                    claim.routingKey(),
                    new Message(claim.payload().getBytes(StandardCharsets.UTF_8), messageProperties),
                    correlationData);
            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(properties.getConfirmTimeoutMs(), TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                results.failed(claim, "Broker rejected publish.");
                log.warn("Payroll event publish failed: eventId={}, outcome=nack", claim.eventId());
                return;
            }
            if (correlationData.getReturned() != null) {
                results.failed(claim, "Broker returned unroutable event.");
                log.warn("Payroll event publish failed: eventId={}, outcome=unroutable", claim.eventId());
                return;
            }
            results.published(claim);
            log.info("Payroll event published: eventId={}, journalReference={}, outcome=confirmed",
                    claim.eventId(), claim.aggregateId());
        } catch (TimeoutException exception) {
            results.failed(claim, "Broker confirm timed out.");
            log.warn("Payroll event publish failed: eventId={}, outcome=timeout", claim.eventId());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            results.failed(claim, "Broker publish interrupted.");
            log.warn("Payroll event publish failed: eventId={}, outcome=interrupted", claim.eventId());
        } catch (ExecutionException | AmqpException exception) {
            results.failed(claim, "Broker publish failed.");
            log.warn("Payroll event publish failed: eventId={}, outcome=error", claim.eventId());
        }
    }
}
