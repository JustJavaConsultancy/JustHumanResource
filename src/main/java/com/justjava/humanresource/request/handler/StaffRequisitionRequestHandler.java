package com.justjava.humanresource.request.handler;
import com.justjava.humanresource.request.dto.CreateWorkflowRequestCommand;
import com.justjava.humanresource.request.entity.*;
import com.justjava.humanresource.request.enums.RequestType;
import com.justjava.humanresource.request.repository.StaffRequisitionDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor public class StaffRequisitionRequestHandler implements WorkflowRequestTypeHandler {
 private final StaffRequisitionDetailRepository repository;
 public RequestType supportedType(){return RequestType.STAFF_REQUISITION;}
 public void validate(CreateWorkflowRequestCommand c){if(c.getStaffRequisition()==null) throw new IllegalArgumentException("Staff requisition details are required.");}
 public void saveDetails(WorkflowRequest r,CreateWorkflowRequestCommand c){var p=c.getStaffRequisition(); StaffRequisitionDetail d=new StaffRequisitionDetail(); d.setWorkflowRequestId(r.getId()); d.setJobTitle(p.getJobTitle()); d.setDepartmentId(p.getDepartmentId()); d.setJobGradeId(p.getJobGradeId()); d.setNumberOfPositions(p.getNumberOfPositions()); d.setEmploymentType(p.getEmploymentType()); d.setTargetStartDate(p.getTargetStartDate()); d.setBudgeted(p.isBudgeted()); d.setEstimatedMonthlyCost(p.getEstimatedMonthlyCost()); d.setReasonForHire(p.getReasonForHire()); d.setReplacementEmployeeId(p.getReplacementEmployeeId()); repository.save(d);}
}
