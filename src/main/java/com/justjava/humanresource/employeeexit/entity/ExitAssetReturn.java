package com.justjava.humanresource.employeeexit.entity;
import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.employeeexit.enums.AssetDisposition;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter;
import java.math.BigDecimal; import java.time.*;
@Getter @Setter @Entity @Table(name="employee_exit_asset_returns",uniqueConstraints=@UniqueConstraint(name="uk_exit_asset",columnNames={"exitCaseId","externalAssetId"}))
public class ExitAssetReturn extends BaseEntity {
 @Column(nullable=false) private Long exitCaseId; @Column(nullable=false,length=100) private String externalAssetId;
 @Column(length=100) private String externalAssetCode; @Column(nullable=false) private String assetName; @Column(length=80) private String category;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=40) private AssetDisposition disposition=AssetDisposition.PENDING;
 private LocalDate expectedReturnDate, returnedDate; @Column(length=500) private String returnCondition;
 @Column(precision=19,scale=2) private BigDecimal assessedValue, recoveryAmount; @Column(length=100) private String externalTransactionReference;
 private Long verifiedByEmployeeId; private LocalDateTime verifiedAt;
}
