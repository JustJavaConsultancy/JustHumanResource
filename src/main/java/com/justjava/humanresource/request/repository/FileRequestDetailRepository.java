package com.justjava.humanresource.request.repository;
import com.justjava.humanresource.request.entity.FileRequestDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface FileRequestDetailRepository extends JpaRepository<FileRequestDetail,Long> { Optional<FileRequestDetail> findByWorkflowRequestId(Long id); }
