package com.justjava.humanresource.hr.service;

import com.justjava.humanresource.hr.dto.CreateJobGradeWithStepsCommand;
import com.justjava.humanresource.hr.dto.CreatePayGroupCommand;
import com.justjava.humanresource.hr.dto.JobGradeResponseDTO;
import com.justjava.humanresource.hr.dto.PayGroupResponseDTO;
import com.justjava.humanresource.hr.entity.Department;
import com.justjava.humanresource.hr.entity.JobGrade;

import java.util.List;
//import com.justjava.humanresource.hr.entity.LeaveType;

public interface SetupService {

    Department createDepartment(String name);

    List<Department> getAllDepartments();

    JobGradeResponseDTO createJobGradeWithSteps(CreateJobGradeWithStepsCommand command);

    List<JobGradeResponseDTO> getAllJobGrades();

/*    LeaveType createLeaveType(
            String code,
            String name,
            int entitlementDays,
            boolean paid,
            boolean requiresApproval
    );*/
    PayGroupResponseDTO createPayGroup(CreatePayGroupCommand command);

}
