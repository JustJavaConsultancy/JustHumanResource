package com.justjava.humanresource.communication.repository;

import com.justjava.humanresource.communication.entity.ChatGroupMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatGroupMemberRepository extends JpaRepository<ChatGroupMember, Long> {
    boolean existsByChatGroup_IdAndEmployee_IdAndRemovedAtIsNull(Long groupId, Long employeeId);
    long countByChatGroup_IdAndRemovedAtIsNull(Long groupId);
    List<ChatGroupMember> findByChatGroup_IdAndRemovedAtIsNullOrderByEmployee_LastNameAscEmployee_FirstNameAsc(Long groupId);
    Optional<ChatGroupMember> findByChatGroup_IdAndEmployee_Id(Long groupId, Long employeeId);
}
