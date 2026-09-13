package com.justjava.humanresource.employeeexit.dto;
import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.NotNull; import lombok.Getter; import lombok.Setter; import java.math.BigDecimal; import java.time.LocalDate;
@Getter @Setter public class EmployeeAssignedAssetCommand {
    @NotNull private Long employeeId;
    @NotBlank private String externalAssetId;
    private String externalAssetCode;
    @NotBlank private String assetName;
    private String category;
    private BigDecimal assessedValue;
    private LocalDate assignedDate;
}