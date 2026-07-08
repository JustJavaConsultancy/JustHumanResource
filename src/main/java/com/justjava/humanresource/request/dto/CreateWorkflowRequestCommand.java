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
 @Valid private ExpenseReimbursementPayload expenseReimbursement;
 @Valid private List<Item> items = new ArrayList<>();
 @Data public static class Item { private String famAssetId; private String itemName; private String description; private String category; @NotNull @DecimalMin(value="0.0001") private BigDecimal quantity; private String unitOfMeasure; @DecimalMin("0") private BigDecimal estimatedUnitCost; @Size(min=3,max=3) private String currency; private String vendorName; private String remarks; }
 @Data public static class StaffRequisitionPayload { @NotBlank private String jobTitle; @NotNull private Long departmentId; private Long jobGradeId; @NotNull @Min(1) private Integer numberOfPositions; @NotNull private StaffEmploymentType employmentType; @NotNull private RequisitionReason requisitionReason; @FutureOrPresent private LocalDate targetStartDate; private boolean budgeted; @DecimalMin("0") private BigDecimal estimatedMonthlyCost; @NotBlank private String reasonForHire; private Long replacementEmployeeId; }
 @Data public static class FileRequestPayload { @NotNull private FileCategory fileCategory; @NotNull private FileConfidentialityLevel confidentialityLevel; @NotNull private FileAccessType requestedAccessType; private boolean retentionRequired; @NotBlank private String purpose; }
 @Data public static class AssetRequestPayload { private String costCenter; @NotNull @FutureOrPresent private LocalDate requiredDate; @NotBlank private String businessJustification; }
 @Data public static class ExpenseReimbursementPayload { @NotNull private LocalDate expenseStartDate; @NotNull private LocalDate expenseEndDate; @NotBlank private String businessPurpose; @NotNull private ExpensePaymentMethod paymentMethod; @NotBlank @Size(min=3,max=3) private String currency; @Valid @NotEmpty private List<ExpenseItemPayload> expenseItems = new ArrayList<>(); }
 @Data public static class ExpenseItemPayload { @NotNull private ExpenseCategory expenseCategory; @NotNull private LocalDate expenseDate; @NotBlank private String description; private String vendorName; @NotNull @DecimalMin("0.01") private BigDecimal amount; private String remarks; }
}
