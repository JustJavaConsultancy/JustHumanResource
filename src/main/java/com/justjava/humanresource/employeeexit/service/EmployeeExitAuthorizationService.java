package com.justjava.humanresource.employeeexit.service;
import com.justjava.humanresource.core.config.AuthenticationManager; import com.justjava.humanresource.employeeexit.entity.*; import com.justjava.humanresource.employeeexit.enums.*; import com.justjava.humanresource.hr.entity.Employee; import com.justjava.humanresource.hr.repository.EmployeeRepository; import lombok.RequiredArgsConstructor; import org.flowable.task.api.Task; import org.springframework.stereotype.Service; import java.util.Collection;
@Service @RequiredArgsConstructor public class EmployeeExitAuthorizationService {
 private final AuthenticationManager auth; private final EmployeeRepository employees;
 public Employee currentEmployee(){Object email=auth.get("email");if(email==null)throw new ExitAccessDeniedException("Authenticated user has no email claim.");return employees.findByEmail(email.toString()).orElseThrow(()->new ExitAccessDeniedException("Authenticated employee record not found."));}
 public boolean isHr(){return auth.isHumanResource()||auth.isJobHR()||auth.isRestrictedHr()||auth.isAdmin();}
 public boolean canView(EmployeeExitCase x,Employee actor){return x.getEmployeeId().equals(actor.getId())||isHr()||auth.isFinancialOfficer()||group("/assetManager")||group("/departmentHead");}
 public boolean canCreateFor(Long employeeId,Employee actor){return employeeId.equals(actor.getId())||isHr();}
 public boolean canSubmit(EmployeeExitCase x,Employee actor){return x.getEmployeeId().equals(actor.getId())||isHr();}
 public boolean canApprove(EmployeeExitCase x,Task task,Employee actor){return "exitApproval".equals(task.getTaskDefinitionKey())&&String.valueOf(actor.getId()).equals(task.getAssignee());}
 public boolean canCompleteClearance(ClearanceType t,Employee actor){return auth.isAdmin()||switch(t){case MANAGER_HANDOVER->group("/departmentHead");case ASSET_AND_FACILITIES->group("/assetManager");case IT_AND_SECURITY->auth.isAdmin();case HR_AND_LEGAL->auth.isHumanResource()||auth.isJobHR()||auth.isRestrictedHr();case PAYROLL_AND_FINANCE->auth.isFinancialOfficer();};}
 public boolean canUploadDocument(EmployeeExitCase x,Employee actor,ExitDocumentVisibility visibility){return isHr()||x.getEmployeeId().equals(actor.getId())&&visibility!=ExitDocumentVisibility.HR_ONLY;}
 public boolean canViewDocument(EmployeeExitCase x,EmployeeExitDocument d,Employee actor){if(isHr())return true;if(d.getVisibility()==ExitDocumentVisibility.HR_ONLY)return false;if(d.getVisibility()==ExitDocumentVisibility.FINANCE_AND_HR)return auth.isFinancialOfficer();return x.getEmployeeId().equals(actor.getId())||auth.isFinancialOfficer();}
 public boolean canDeleteDocument(EmployeeExitCase x,EmployeeExitDocument d,Employee actor){return isHr();}
 public boolean canManageSettlement(){return auth.isFinancialOfficer()||auth.isAdmin();} public boolean canManageAssets(){return group("/assetManager")||auth.isAdmin();}
 public boolean canViewReports(){return isHr()||auth.isFinancialOfficer()||group("/assetManager");}
 public void require(boolean allowed,String message){if(!allowed)throw new ExitAccessDeniedException(message);} private boolean group(String g){Object v=auth.get("groups");return v instanceof Collection<?> c&&c.contains(g);}
}
