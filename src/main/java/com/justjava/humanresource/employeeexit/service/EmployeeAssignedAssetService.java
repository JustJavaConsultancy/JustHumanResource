package com.justjava.humanresource.employeeexit.service;
import com.justjava.humanresource.employeeexit.dto.EmployeeAssignedAssetCommand; import com.justjava.humanresource.employeeexit.entity.EmployeeAssignedAsset; import com.justjava.humanresource.employeeexit.repository.EmployeeAssignedAssetRepository; import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import java.util.List;
@Service @RequiredArgsConstructor public class EmployeeAssignedAssetService {
    private final EmployeeAssignedAssetRepository assets;
    @Transactional(readOnly=true) public List<EmployeeAssignedAsset> findByEmployee(Long employeeId){return assets.findByEmployeeIdOrderByAssetName(employeeId);}
    @Transactional(readOnly=true) public List<EmployeeAssignedAsset> findActiveByEmployee(Long employeeId){return assets.findByEmployeeIdAndActiveTrue(employeeId);}

    @Transactional public EmployeeAssignedAsset addOrUpdate(EmployeeAssignedAssetCommand c){
        EmployeeAssignedAsset a=assets.findByEmployeeIdAndExternalAssetId(c.getEmployeeId(),c.getExternalAssetId()).orElseGet(EmployeeAssignedAsset::new);
        a.setEmployeeId(c.getEmployeeId());a.setExternalAssetId(c.getExternalAssetId());a.setExternalAssetCode(c.getExternalAssetCode());a.setAssetName(c.getAssetName());a.setCategory(c.getCategory());a.setAssessedValue(c.getAssessedValue());a.setAssignedDate(c.getAssignedDate());a.setActive(true);
        return assets.save(a);
    }
    @Transactional public EmployeeAssignedAsset setActive(Long id,boolean active){EmployeeAssignedAsset a=assets.findById(id).orElseThrow(()->new IllegalArgumentException("Assigned asset not found."));a.setActive(active);return assets.save(a);}
}