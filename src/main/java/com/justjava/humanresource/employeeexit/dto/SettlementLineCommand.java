package com.justjava.humanresource.employeeexit.dto;
import com.justjava.humanresource.employeeexit.enums.SettlementLineType; import jakarta.validation.constraints.*; import lombok.Data; import java.math.BigDecimal;
@Data public class SettlementLineCommand { @NotNull private SettlementLineType lineType; private String componentCode; @NotBlank private String description; @NotNull @DecimalMin("0.00") private BigDecimal amount; private boolean earning; private boolean taxable; private boolean manualAdjustment; private String adjustmentReason; private String source; }
