# Retro Processing Implementation - Complete Flow

## Summary
The retro processing implementation is **already in place** and working correctly. The flow threads the effective date through the system properly.

## Flow Overview

### 1. Call Sites (EmployeeServiceImpl) ✅
**Location**: `EmployeeServiceImpl` 
**Status**: CORRECT - Already passes effectiveDate

Methods that correctly pass effectiveDate:
- `changePayGroup(employeeId, payGroup, effectiveDate)` → passes `effectiveDate`
- `changeJobStep(employeeId, jobStepId, effectiveDate)` → passes `effectiveDate`  
- `changePosition(employeeId, jobStepId, payGroupId, effectiveFrom)` → passes `effectiveFrom`
- `changeEmploymentStatus(employeeId, status, effectiveDate)` → passes `effectiveDate`
- `suspendEmployee(employeeId, fromDate, toDate)` → passes `fromDate`

All these call: `payrollChangeOrchestrator.recalculateForEmployee(employeeId, effectiveDate)`

### 2. PayrollChangeOrchestratorImpl ✅
**Location**: `PayrollChangeOrchestratorImpl.recalculateForEmployee()`
**Status**: CORRECT - Passes effectiveDate to dispatcher

```java
public void recalculateForEmployee(Long employeeId, LocalDate effectiveDate) {
    updateEmployeePositionHistory(employeeId, effectiveDate);
    safeRequestPayroll(employeeId, effectiveDate);  // ← passes effectiveDate
}

private void safeRequestPayroll(Long employeeId, LocalDate effectiveDate) {
    dispatcher.requestPayroll(employeeId, effectiveDate);  // ← effectiveDate threaded
}
```

### 3. PayrollMessageDispatcher ✅  
**Location**: `PayrollMessageDispatcherImpl.requestPayroll()`
**Status**: CORRECT - Stores effectiveDate as retroEffectiveDate in Flowable variables

```java
public void requestPayroll(Long employeeId, LocalDate effectiveDate) {
    Map<String, Object> vars = new HashMap<>();
    vars.put("employeeId", employeeId);
    vars.put("payrollDate", period.getPeriodEnd());        // ← payroll period end
    vars.put("retroEffectiveDate", effectiveDate);         // ← user's effective date stored!
    
    runtimeService.messageEventReceived("PAYROLL_MESSAGE", execution.getId(), vars);
}
```

### 4. InitializePayrollDelegate ✅
**Location**: `InitializePayrollDelegate.execute()`
**Status**: CORRECT - Retrieves retroEffectiveDate from Flowable variables

```java
public void execute(DelegateExecution execution) {
    LocalDate payrollDate = (LocalDate) execution.getVariable("payrollDate");
    
    // Retrieve optional retroEffectiveDate
    LocalDate retroEffectiveDate = null;
    Object retroVar = execution.getVariable("retroEffectiveDate");
    if (retroVar instanceof LocalDate) {
        retroEffectiveDate = (LocalDate) retroVar;
    }
    
    Long payrollRunId = payrollService.initializePayrollRun(
        employeeId,
        payrollDate,
        retroEffectiveDate,        // ← threaded to orchestration
        processInstanceId
    );
}
```

### 5. PayrollOrchestrationService.initializePayrollRun() ✅
**Location**: `PayrollOrchestrationServiceImpl.initializePayrollRun()`
**Status**: CORRECT - Stores retroEffectiveDate on PayrollRun when appropriate

For AMENDMENT runs (existing payroll):
```java
// Store retro date only when the change pre-dates the current period
if (retroEffectiveDate != null 
    && retroEffectiveDate.isBefore(openPeriod.getPeriodStart())) {
    amendment.setRetroEffectiveDate(retroEffectiveDate);
}
```

For ORIGINAL runs (new payroll):
```java
// Store retro date only when the change pre-dates the current period
if (retroEffectiveDate != null 
    && retroEffectiveDate.isBefore(openPeriod.getPeriodStart())) {
    run.setRetroEffectiveDate(retroEffectiveDate);
}
```

## Key Design Points

1. **retroEffectiveDate vs payrollDate**:
   - `payrollDate` = end of the open period (for current/catch-up runs)
   - `retroEffectiveDate` = user-provided effective date (when triggering a retro run)

2. **Retro Detection Logic**:
   - If `retroEffectiveDate` is NULL → Normal run, no retro adjustments
   - If `retroEffectiveDate` is within current period → Normal run, no retro adjustments  
   - If `retroEffectiveDate` is BEFORE current period start → Retro run, store on PayrollRun

3. **Downstream Calculation**:
   - CalculateEarningsDelegate accesses the PayrollRun's `retroEffectiveDate`
   - Uses it to determine whether to add catch-up RETRO_ADJ line items

## What's Already Done

✅ PayrollMessageDispatcher threads `retroEffectiveDate` correctly
✅ InitializePayrollDelegate retrieves it from Flowable variables  
✅ PayrollOrchestrationService stores it on PayrollRun conditionally
✅ Call sites (EmployeeServiceImpl) pass actual effective dates

## What May Need Future Work

1. **Bulk/Administrative Operations** - Methods in `SetupServiceImpl`, `EmployeeUploadServiceImpl` currently use `LocalDate.now()`, which is appropriate since these are immediate changes

2. **SalaryChangePayrollListener** - Currently not used in codebase (dead code), but if activated, needs to pass `event.getLocalDate()` instead of `LocalDate.now()`

3. **KPI Operations** - KpiCsvUploadService and KpiMeasurementService correctly use `LocalDate.now()` for immediate recalculations

## Verification

The implementation is complete and correct. The effective date flows through:

```
User API Call (with effectiveDate)
    ↓
EmployeeServiceImpl.changeXxx(effectiveDate)
    ↓
PayrollChangeOrchestratorImpl.recalculateForEmployee(effectiveDate)
    ↓
PayrollMessageDispatcher.requestPayroll(effectiveDate)
    ↓
Flowable vars: retroEffectiveDate = effectiveDate
    ↓
InitializePayrollDelegate reads retroEffectiveDate
    ↓
PayrollOrchestrationService stores on PayrollRun (if before period)
    ↓
CalculateEarningsDelegate uses for retro adjustments
```

This implementation correctly handles retro processing!
