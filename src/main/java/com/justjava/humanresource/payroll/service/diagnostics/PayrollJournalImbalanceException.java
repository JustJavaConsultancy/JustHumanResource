package com.justjava.humanresource.payroll.service.diagnostics;

public class PayrollJournalImbalanceException extends IllegalStateException {
    private final PayrollJournalDiagnostics diagnostics;

    public PayrollJournalImbalanceException(PayrollJournalDiagnostics diagnostics) {
        super("Generated payroll journal is unbalanced for period "
                + diagnostics.getPeriodStart() + " to " + diagnostics.getPeriodEnd()
                + ". Debit: " + diagnostics.getTotalDebit()
                + ", Credit: " + diagnostics.getTotalCredit()
                + ", Difference: " + diagnostics.getDifference()
                + ". Review the diagnostic details below.");
        this.diagnostics = diagnostics;
    }

    public PayrollJournalDiagnostics getDiagnostics() {
        return diagnostics;
    }
}
