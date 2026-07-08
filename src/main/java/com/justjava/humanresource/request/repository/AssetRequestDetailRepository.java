package com.justjava.humanresource.request.repository;
import com.justjava.humanresource.request.entity.AssetRequestDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface AssetRequestDetailRepository extends JpaRepository<AssetRequestDetail,Long> { Optional<AssetRequestDetail> findByWorkflowRequestId(Long id); }
