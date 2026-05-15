package com.justjava.humanresource.payroll.util;

import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class YearMonthAttributeConverterTest {

    private final YearMonthAttributeConverter converter = new YearMonthAttributeConverter();

    @Test
    void convertToDatabaseColumn_shouldConvertYearMonthToString() {
        String result = converter.convertToDatabaseColumn(YearMonth.of(2026, 5));

        assertEquals("2026-05", result);
    }

    @Test
    void convertToDatabaseColumn_shouldReturnNullForNullInput() {
        assertNull(converter.convertToDatabaseColumn(null));
    }

    @Test
    void convertToEntityAttribute_shouldConvertStringToYearMonth() {
        YearMonth result = converter.convertToEntityAttribute("2026-05");

        assertEquals(YearMonth.of(2026, 5), result);
    }

    @Test
    void convertToEntityAttribute_shouldReturnNullForNullInput() {
        assertNull(converter.convertToEntityAttribute(null));
    }
}
