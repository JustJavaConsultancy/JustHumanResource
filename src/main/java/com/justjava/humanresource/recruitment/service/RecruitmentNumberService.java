package com.justjava.humanresource.recruitment.service;

import org.springframework.stereotype.Service;
import java.time.Year;
import java.util.UUID;

@Service
public class RecruitmentNumberService {
    public String candidateNumber() { return "CAN-%d-%s".formatted(Year.now().getValue(), shortId()); }
    public String applicationNumber() { return "APP-%d-%s".formatted(Year.now().getValue(), shortId()); }
    private String shortId() { return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(); }
}
