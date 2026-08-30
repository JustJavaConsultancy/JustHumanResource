package com.justjava.humanresource.communication.repository;

import com.justjava.humanresource.communication.entity.HrBroadcast;
import com.justjava.humanresource.communication.entity.HrBroadcastStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HrBroadcastRepository extends JpaRepository<HrBroadcast, Long> {
    List<HrBroadcast> findByStatusOrderByCreatedAtDesc(HrBroadcastStatus status);
}
