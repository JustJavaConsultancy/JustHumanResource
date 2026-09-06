package com.justjava.humanresource.communication.repository;

import com.justjava.humanresource.communication.entity.ChatGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatGroupRepository extends JpaRepository<ChatGroup, Long> {

    @Query("SELECT cg FROM ChatGroup cg WHERE cg.status = com.justjava.humanresource.core.enums.RecordStatus.ACTIVE ORDER BY cg.createdAt DESC")
    List<ChatGroup> findAllActiveOrderByCreatedAtDesc();

    @Query("SELECT cg FROM ChatGroup cg JOIN ChatGroupMember cgm ON cg.id = cgm.group.id WHERE cgm.employee.id = :employeeId AND cg.status = com.justjava.humanresource.core.enums.RecordStatus.ACTIVE ORDER BY cg.createdAt DESC")
    List<ChatGroup> findActiveGroupsByEmployeeId(@Param("employeeId") Long employeeId);

    @Query("SELECT cg FROM ChatGroup cg WHERE cg.createdByEmail = :email AND cg.status = com.justjava.humanresource.core.enums.RecordStatus.ACTIVE ORDER BY cg.createdAt DESC")
    List<ChatGroup> findActiveGroupsByCreatorEmail(@Param("email") String email);

    Optional<ChatGroup> findByIdAndStatus(Long id, com.justjava.humanresource.core.enums.RecordStatus status);
}