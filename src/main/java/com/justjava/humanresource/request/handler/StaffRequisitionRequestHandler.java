package com.justjava.humanresource.request.handler;
import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.hr.repository.DepartmentRepository;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.hr.repository.JobGradeRepository;
import com.justjava.humanresource.request.dto.CreateWorkflowRequestCommand;
import com.justjava.humanresource.request.entity.*;
import com.justjava.humanresource.request.enums.RequisitionReason;
import com.justjava.humanresource.request.enums.RequestType;
import com.justjava.humanresource.request.repository.StaffRequisitionDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor public class StaffRequisitionRequestHandler implements WorkflowRequestTypeHandler {
 private final StaffRequisitionDetailRepository repository;
 private final DepartmentRepository departmentRepository;
 private final JobGradeRepository jobGradeRepository;
 private final EmployeeRepository employeeRepository;
 public RequestType supportedType(){return RequestType.STAFF_REQUISITION;}
 public void validate(CreateWorkflowRequestCommand c){if(c.getStaffRequisition()==null) throw new IllegalArgumentException("Staff requisition details are required.");var p=c.getStaffRequisition();var department=departmentRepository.findById(p.getDepartmentId()).orElseThrow(()->new IllegalArgumentException("Selected department was not found."));if(department.getStatus()!= RecordStatus.ACTIVE)throw new IllegalArgumentException("Selected department is not active.");if(p.getJobGradeId()!=null){var grade=jobGradeRepository.findById(p.getJobGradeId()).orElseThrow(()->new IllegalArgumentException("Selected job grade was not found."));if(grade.getDepartment()!=null&&!grade.getDepartment().getId().equals(p.getDepartmentId()))throw new IllegalArgumentException("Selected job grade does not belong to the selected department.");}if(p.getRequisitionReason()== RequisitionReason.REPLACEMENT&&p.getReplacementEmployeeId()==null)throw new IllegalArgumentException("Replacement employee is required for replacement requisitions.");if(p.getReplacementEmployeeId()!=null){var employee=employeeRepository.findById(p.getReplacementEmployeeId()).orElseThrow(()->new IllegalArgumentException("Selected replacement employee was not found."));if(employee.getStatus()!=RecordStatus.ACTIVE)throw new IllegalArgumentException("Selected replacement employee is not active.");}}
 public void saveDetails(WorkflowRequest r,CreateWorkflowRequestCommand c){var p=c.getStaffRequisition(); StaffRequisitionDetail d=new StaffRequisitionDetail(); d.setWorkflowRequestId(r.getId()); d.setJobTitle(p.getJobTitle()); d.setDepartmentId(p.getDepartmentId()); d.setJobGradeId(p.getJobGradeId()); d.setNumberOfPositions(p.getNumberOfPositions()); d.setEmploymentType(p.getEmploymentType()); d.setRequisitionReason(p.getRequisitionReason()); d.setTargetStartDate(p.getTargetStartDate()); d.setBudgeted(p.isBudgeted()); d.setEstimatedMonthlyCost(p.getEstimatedMonthlyCost()); d.setReasonForHire(p.getReasonForHire()); d.setReplacementEmployeeId(p.getReplacementEmployeeId()); repository.save(d);}
}
