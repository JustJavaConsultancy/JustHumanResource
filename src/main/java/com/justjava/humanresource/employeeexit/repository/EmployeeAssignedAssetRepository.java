package com.justjava.humanresource.employeeexit.repository; import com.justjava.humanresource.employeeexit.entity.EmployeeAssignedAsset; import org.springframework.data.jpa.repository.JpaRepository; import java.util.List; import java.util.Optional;
public interface EmployeeAssignedAssetRepository extends JpaRepository<EmployeeAssignedAsset,Long>{
    List<EmployeeAssignedAsset> findByEmployeeIdAndActiveTrue(Long employeeId);
    List<EmployeeAssignedAsset> findByEmployeeIdOrderByAssetName(Long employeeId);
    Optional<EmployeeAssignedAsset> findByEmployeeIdAndExternalAssetId(Long employeeId,String externalAssetId);
}