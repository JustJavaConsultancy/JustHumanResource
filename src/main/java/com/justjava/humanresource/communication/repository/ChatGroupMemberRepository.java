package com.justjava.humanresource.communication.repository;

import com.justjava.humanresource.communication.entity.ChatGroup;
import com.justjava.humanresource.communication.entity.ChatGroupMember;
import com.justjava.humanresource.communication.entity.ChatGroupMemberRole;
import com.justjava.humanresource.hr.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatGroupMemberRepository extends JpaRepository<ChatGroupMember, Long> {

    @Query("SELECT cgm FROM ChatGroupMember cgm WHERE cgm.group.id = :groupId ORDER BY cgm.joinedAt ASC")
    List<ChatGroupMember> findByGroupIdOrderByJoinedAtAsc(@Param("groupId") Long groupId);

    @Query("SELECT cgm FROM ChatGroupMember cgm WHERE cgm.group.id = :groupId AND cgm.employee.id = :employeeId")
    Optional<ChatGroupMember> findByGroupIdAndEmployeeId(@Param("groupId") Long groupId, @Param("employeeId") Long employeeId);

    @Query("SELECT cgm FROM ChatGroupMember cgm WHERE cgm.group.id = :groupId AND cgm.role = com.justjava.humanresource.communication.entity.ChatGroupMemberRole.ADMIN")
    List<ChatGroupMember> findAdminsByGroupId(@Param("groupId") Long groupId);

    @Query("SELECT cgm FROM ChatGroupMember cgm WHERE cgm.employee.id = :employeeId")
    List<ChatGroupMember> findByEmployeeId(@Param("employeeId") Long employeeId);

    boolean existsByGroupIdAndEmployeeId(Long groupId, Long employeeId);

    boolean existsByGroupIdAndEmployeeIdAndRole(Long groupId, Long employeeId, com.justjava.humanresource.communication.entity.ChatGroupMemberRole role);

    long countByGroupId(Long groupId);

    void deleteByGroupIdAndEmployeeId(Long groupId, Long employeeId);
}