package com.justjava.humanresource.promotion;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.promotion.entity.PromotionActivityLog;
import com.justjava.humanresource.promotion.entity.PromotionRequest;
import com.justjava.humanresource.promotion.enums.PromotionActivityType;
import com.justjava.humanresource.promotion.repository.PromotionActivityLogRepository;
import com.justjava.humanresource.promotion.service.PromotionActivityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PromotionActivityServiceTest {

    @Mock PromotionActivityLogRepository activityLogRepository;

    private PromotionActivityService service;

    @BeforeEach
    void setUp() {
        service = new PromotionActivityService(activityLogRepository);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void recordsRequestMetadataWhenWebRequestExists() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.15");
        request.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.15");
        request.addHeader("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) Mobile/15E148");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        service.record(promotionRequest(), PromotionActivityType.CREATED, actor(), "Created.");

        PromotionActivityLog log = savedLog();
        assertThat(log.getIpAddress()).isEqualTo("203.0.113.10");
        assertThat(log.getUserAgent()).contains("iPhone");
        assertThat(log.getDeviceMetadata()).isEqualTo("Mobile");
        assertThat(log.getCreatedAt()).isNotNull();
    }

    @Test
    void leavesRequestMetadataEmptyForSystemActivity() {
        service.record(promotionRequest(), PromotionActivityType.NOTIFICATION_SENT, null, "Queued.");

        PromotionActivityLog log = savedLog();
        assertThat(log.getIpAddress()).isNull();
        assertThat(log.getUserAgent()).isNull();
        assertThat(log.getDeviceMetadata()).isNull();
        assertThat(log.getActor()).isNull();
    }

    private PromotionActivityLog savedLog() {
        ArgumentCaptor<PromotionActivityLog> captor = ArgumentCaptor.forClass(PromotionActivityLog.class);
        verify(activityLogRepository).save(captor.capture());
        return captor.getValue();
    }

    private PromotionRequest promotionRequest() {
        PromotionRequest request = new PromotionRequest();
        request.setId(44L);
        return request;
    }

    private Employee actor() {
        Employee employee = new Employee();
        employee.setId(7L);
        employee.setFirstName("Ada");
        employee.setLastName("Admin");
        return employee;
    }
}
