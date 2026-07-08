package com.justjava.humanresource.request.repository;
import com.justjava.humanresource.request.entity.StaffRequisitionDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface StaffRequisitionDetailRepository extends JpaRepository<StaffRequisitionDetail,Long> { Optional<StaffRequisitionDetail> findByWorkflowRequestId(Long id); }
