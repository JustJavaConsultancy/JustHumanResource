package com.justjava.humanresource.loan.service;

public interface LoanNumberService {

    /**
     * Returns the next application number, e.g. LOAN-2026-0001.
     * Joins the caller's transaction, so a rollback also releases the number (no gaps).
     */
    String generateApplicationNumber();
}
