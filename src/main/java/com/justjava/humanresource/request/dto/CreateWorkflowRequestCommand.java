package com.justjava.humanresource.request.dto;

import com.justjava.humanresource.request.enums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class CreateWorkflowRequestCommand {
 @NotNull private RequestType requestType;
 @NotBlank @Size(max=200) private String title;
 @Size(max=10000) private String description;
 private RequestPriority priority = RequestPriority.NORMAL;
 private Long departmentId;
 @Valid private StaffRequisitionPayload staffRequisition;
 @Valid private FileRequestPayload fileRequest;
 @Valid private AssetRequestPayload assetRequest;
 @Valid private List<Item> items = new ArrayList<>();
 @Data public static class Item { @NotBlank private String itemName; private String description; private String category; @NotNull @DecimalMin(value="0.0001") private BigDecimal quantity; private String unitOfMeasure; @DecimalMin("0") private BigDecimal estimatedUnitCost; @Size(min=3,max=3) private String currency; private String vendorName; private String remarks; }
 @Data public static class StaffRequisitionPayload { @NotBlank private String jobTitle; @NotNull private Long departmentId; private Long jobGradeId; @NotNull @Min(1) private Integer numberOfPositions; @NotBlank private String employmentType; @FutureOrPresent private LocalDate targetStartDate; private boolean budgeted; @DecimalMin("0") private BigDecimal estimatedMonthlyCost; @NotBlank private String reasonForHire; private Long replacementEmployeeId; }
 @Data public static class FileRequestPayload { @NotBlank private String fileCategory; @NotBlank private String confidentialityLevel; @NotBlank private String requestedAccessType; private boolean retentionRequired; @NotBlank private String purpose; }
 @Data public static class AssetRequestPayload { @NotBlank private String costCenter; @NotNull @FutureOrPresent private LocalDate requiredDate; @NotBlank private String businessJustification; }
}
