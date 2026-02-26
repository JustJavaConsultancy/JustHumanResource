package com.justjava.humanresource.hr.repository;

import com.justjava.humanresource.hr.entity.EmergencyContact;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmergencyContactRepository extends JpaRepository<EmergencyContact, Long> {
}