package com.justjava.humanresource.request.repository;
import com.justjava.humanresource.request.entity.WorkflowRequestType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface WorkflowRequestTypeRepository extends JpaRepository<WorkflowRequestType,Long> { @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select t from WorkflowRequestType t where t.code=:code")
 Optional<WorkflowRequestType> findByCodeForUpdate(@org.springframework.data.repository.query.Param("code") String code);
 Optional<WorkflowRequestType> findByCode(String code); List<WorkflowRequestType> findByEnabledTrueOrderByName(); }
