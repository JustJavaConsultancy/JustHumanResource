package com.justjava.humanresource.payroll.statutory.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class TaxBandUploadRowDTO {
    private int rowNumber;
    private BigDecimal lowerBound;
    private BigDecimal upperBound; // null => open-ended (only valid on last row)
    private BigDecimal rate;
}