package com.justjava.humanresource.promotion;

import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PromotionControllerTest {

    private final PromotionController controller = new PromotionController();

    @Test
    void promotionRootOpensDashboardSection() {
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.getPromotions(model);

        assertEquals("promotion/main", view);
        assertEquals("Promotion Management", model.getAttribute("title"));
        assertEquals("dashboard", model.getAttribute("initialSection"));
    }

    @Test
    void dedicatedPromotionRoutesOpenExpectedSections() {
        assertSection("requests", controller::requests);
        assertSection("approvals", controller::approvals);
        assertSection("reports", controller::reports);
        assertSection("configuration", controller::configuration);
        assertSection("payrollHandoffs", controller::payrollHandoffs);
    }

    private void assertSection(String expectedSection, PageRoute route) {
        ConcurrentModel model = new ConcurrentModel();

        String view = route.open(model);

        assertEquals("promotion/main", view);
        assertEquals(expectedSection, model.getAttribute("initialSection"));
    }

    private interface PageRoute {
        String open(ConcurrentModel model);
    }
}
