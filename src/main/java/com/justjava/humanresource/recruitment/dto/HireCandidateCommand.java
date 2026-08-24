package com.justjava.humanresource.recruitment.dto;

import lombok.Data;
import java.util.List;

@Data
public class HireCandidateCommand {
    private Long departmentId;
    private Long jobStepId;
    private Long payGroupId;
    private Long managerId;
    private List<String> groups;
    private String tinNumber;
    private String rsaPin;
    private String pfa;
    private String ninNumber;
    private String bvnNumber;
    private String nextOfKinName;
    private String nextOfKinPhoneNumber;
    private String nextOfKinEmail;
    private String nextOfKinAddress;
    private String guarantorName;
    private String guarantorPhoneNumber;
    private String guarantorEmail;
    private String guarantorAddress;
    private String guarantorNinNumber;
}
