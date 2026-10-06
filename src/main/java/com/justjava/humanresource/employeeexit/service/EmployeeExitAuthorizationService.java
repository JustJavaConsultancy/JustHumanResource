package com.justjava.humanresource.employeeexit.service;
import com.justjava.humanresource.core.config.AuthenticationManager; import com.justjava.humanresource.employeeexit.entity.*; import com.justjava.humanresource.employeeexit.enums.*; import com.justjava.humanresource.hr.entity.Employee; import com.justjava.humanresource.hr.repository.EmployeeRepository; import lombok.RequiredArgsConstructor; import org.flowable.task.api.Task; import org.springframework.stereotype.Service; import java.util.Collection;
@Service @RequiredArgsConstructor public class EmployeeExitAuthorizationService {
 private final AuthenticationManager auth; private final EmployeeRepository employees; private final org.flowable.engine.TaskService taskService;
 public Employee currentEmployee(){Object email=auth.get("email");if(email==null)throw new ExitAccessDeniedException("Authenticated user has no email claim.");return employees.findByEmail(email.toString()).orElseThrow(()->new ExitAccessDeniedException("Authenticated employee record not found."));}
 public boolean isHr(){return auth.isHumanResource()||auth.isJobHR()||auth.isRestrictedHr()||auth.isAdmin();}
 public boolean isAdmin(){return auth.isAdmin();}
 public boolean isDepartmentHead(){return group("departmentHead");}
 public boolean canView(EmployeeExitCase x,Employee actor){return x.getEmployeeId().equals(actor.getId())||isHr()||auth.isFinancialOfficer()||group("assetManager")||group("departmentHead");}
 public boolean canCreateFor(Long employeeId,Employee actor){return employeeId.equals(actor.getId())||isHr();}
 public boolean canSubmit(EmployeeExitCase x,Employee actor){return x.getEmployeeId().equals(actor.getId())||isHr();}
 public boolean canApprove(EmployeeExitCase x,Task task,Employee actor){return "exitApproval".equals(task.getTaskDefinitionKey())&&String.valueOf(actor.getId()).equals(task.getAssignee());}
 // HR gets the same all-clearance-types access as admin (isHr() already includes isAdmin()).
 // Department head/asset manager/finance remain scoped to their one clearance type as before.
 public boolean canCompleteClearance(ClearanceType t,Employee actor){return isHr()||switch(t){case MANAGER_HANDOVER->group("departmentHead");case ASSET_AND_FACILITIES->group("assetManager");case IT_AND_SECURITY->auth.isAdmin();case HR_AND_LEGAL->auth.isHumanResource()||auth.isJobHR()||auth.isRestrictedHr();case PAYROLL_AND_FINANCE->auth.isFinancialOfficer();};}
 public boolean canUploadDocument(EmployeeExitCase x,Employee actor,ExitDocumentVisibility visibility){return isHr()||x.getEmployeeId().equals(actor.getId())&&visibility!=ExitDocumentVisibility.HR_ONLY;}
 public boolean canViewDocument(EmployeeExitCase x,EmployeeExitDocument d,Employee actor){if(isHr())return true;if(d.getVisibility()==ExitDocumentVisibility.HR_ONLY)return false;if(d.getVisibility()==ExitDocumentVisibility.FINANCE_AND_HR)return auth.isFinancialOfficer();return x.getEmployeeId().equals(actor.getId())||auth.isFinancialOfficer();}
 public boolean canDeleteDocument(EmployeeExitCase x,EmployeeExitDocument d,Employee actor){return isHr();}
 public boolean canManageSettlement(){return auth.isFinancialOfficer()||auth.isAdmin();} public boolean canManageAssets(){return isHr()||group("assetManager")||auth.isAdmin();}
 public boolean canViewReports(){return isHr()||auth.isFinancialOfficer()||group("assetManager");}
 // ---- Pending-approval access: department head (managerClearance), asset manager (assetClearance), and
 // line-manager/approver (exitApproval task assigned to the actor). Live Flowable tasks are the source of truth.
 private long countActive(String taskKey,String assignee){var q=taskService.createTaskQuery().taskDefinitionKey(taskKey).active();if(assignee!=null)q=q.taskAssignee(assignee);return q.count();}
 public boolean hasPendingApprovals(Employee actor){return countActive("exitApproval",String.valueOf(actor.getId()))>0||(group("departmentHead")&&countActive("managerClearance",null)>0)||(group("assetManager")&&countActive("assetClearance",null)>0);}
 public java.util.List<Task> pendingApprovalTasks(Employee actor){var r=new java.util.ArrayList<Task>();r.addAll(taskService.createTaskQuery().taskDefinitionKey("exitApproval").taskAssignee(String.valueOf(actor.getId())).active().list());if(group("departmentHead"))r.addAll(taskService.createTaskQuery().taskDefinitionKey("managerClearance").active().list());if(group("assetManager"))r.addAll(taskService.createTaskQuery().taskDefinitionKey("assetClearance").active().list());return r;}
 // Department heads and asset managers have standing access (like HR/finance): full list, any time, incl. completed exits.
 public boolean hasStandingExitAccess(){return group("departmentHead")||group("assetManager");}
 // Full, unscoped exit list: HR/admin, finance, department head, asset manager.
 public boolean canViewFullExitList(){return isHr()||canManageSettlement()||hasStandingExitAccess();}
 // Line-manager approvers have no standing role, so their access is tied to a pending exitApproval task.
 public boolean hasPendingExitApproval(Employee actor){return countActive("exitApproval",String.valueOf(actor.getId()))>0;}
 public boolean canViewExitList(Employee actor){return canViewFullExitList()||hasPendingExitApproval(actor);}
 // Drives the link on the employee self-service exit page.
 public boolean canAccessExitWorkflow(Employee actor){return hasStandingExitAccess()||hasPendingExitApproval(actor);}

 public void require(boolean allowed,String message){if(!allowed)throw new ExitAccessDeniedException(message);} private boolean group(String g){Object v=auth.get("groups");return v instanceof Collection<?> c&&c.contains(g);}


 public boolean canViewSelfServiceExit(EmployeeExitCase exit,Employee actor){return exit.getEmployeeId().equals(actor.getId());}
 public boolean canViewOperationalExit(EmployeeExitCase exit,Employee actor,Collection<Task> activeTasks){return isHr()||auth.isFinancialOfficer()||group("assetManager")||group("departmentHead")||isAssignedActiveTask(actor,activeTasks);}
 private boolean isAssignedActiveTask(Employee actor,Collection<Task> activeTasks){return activeTasks!=null&&activeTasks.stream().anyMatch(t->String.valueOf(actor.getId()).equals(t.getAssignee()));}


 public boolean canManageExitCase(Employee actor){return isHr();}
 public boolean canViewSettlementSection(Employee actor){return isHr()||auth.isFinancialOfficer();}
 public boolean canUseSettlementActions(Employee actor){return isHr()||canManageSettlement();}
 public boolean canViewAssetSection(Employee actor){return isHr()||group("assetManager")||auth.isFinancialOfficer();}
 public boolean canUseAssetActions(Employee actor){return canManageAssets();}
 public boolean canViewHandoverSection(Employee actor){return isHr()||group("departmentHead");}
 public boolean canUseHandoverActions(Employee actor){return isHr()||group("departmentHead");}
 public boolean canResolveExceptions(Employee actor){return isHr();}
}