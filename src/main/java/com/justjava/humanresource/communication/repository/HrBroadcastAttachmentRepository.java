package com.justjava.humanresource.communication.repository;

import com.justjava.humanresource.communication.entity.HrBroadcastAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface HrBroadcastAttachmentRepository extends JpaRepository<HrBroadcastAttachment, Long> {
    List<HrBroadcastAttachment> findAllByOrderByUploadedAtDesc();
    List<HrBroadcastAttachment> findByBroadcast_IdInOrderByUploadedAtAsc(Collection<Long> broadcastIds);
    List<HrBroadcastAttachment> findByBroadcast_IdOrderByUploadedAtAsc(Long broadcastId);
}
