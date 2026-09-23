package com.justjava.humanresource.kpi.repositories;

import com.justjava.humanresource.kpi.entity.KpiAppraisalLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KpiAppraisalLineRepository extends JpaRepository<KpiAppraisalLine, Long> {
    List<KpiAppraisalLine> findByAppraisal_IdOrderByKpi_NameAsc(Long appraisalId);
    Optional<KpiAppraisalLine> findByAppraisal_IdAndKpi_Id(Long appraisalId, Long kpiId);
}
