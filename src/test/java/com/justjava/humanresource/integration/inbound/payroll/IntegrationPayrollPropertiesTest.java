package com.justjava.humanresource.integration.inbound.payroll;

import com.justjava.humanresource.integration.inbound.payroll.config.IntegrationPayrollProperties;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;



class IntegrationPayrollPropertiesTest {
    @Test
    void payrollAccountingCurrencyIsAlwaysNaira() {
        assertThat(new IntegrationPayrollProperties().getCurrency()).isEqualTo("NGN");
    }
}
