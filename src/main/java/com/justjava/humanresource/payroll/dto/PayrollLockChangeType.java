package com.justjava.humanresource.payroll.dto;

/**
 * How an employee's payroll differs between the moment HR locked the
 * period and now.
 */
public enum PayrollLockChangeType {

    /** Had a baseline run; the latest run is POSTED with different gross, deductions or net. */
    CHANGED,

    /** Latest run is newer and not yet POSTED (amendment still being calculated). */
    RECALCULATING,

    /** Gross, deductions and net are identical, but individual pay items differ. */
    LINE_ITEMS_CHANGED,

    /** Has a run now but had none at lock time. */
    ADDED,

    /** Had a run at lock time but has none now. */
    REMOVED
}