package com.justjava.humanresource.request.repository;
import com.justjava.humanresource.request.entity.WorkflowRequestType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface WorkflowRequestTypeRepository extends JpaRepository<WorkflowRequestType,Long> { Optional<WorkflowRequestType> findByCode(String code); List<WorkflowRequestType> findByEnabledTrueOrderByName(); }
