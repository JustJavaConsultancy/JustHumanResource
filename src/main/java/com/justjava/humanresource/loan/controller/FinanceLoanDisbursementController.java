package com.justjava.humanresource.loan.controller;

import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.loan.dto.LoanConfirmDisbursementCommand;
import com.justjava.humanresource.loan.dto.LoanDisbursementFilter;
import com.justjava.humanresource.loan.dto.LoanDisbursementResponse;
import com.justjava.humanresource.loan.enums.LoanDisbursementStatus;
import com.justjava.humanresource.loan.service.LoanDisbursementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Finance loan disbursement queue, history, paid confirmation and bulk CSV export.
 * The list and export share one {@link LoanDisbursementFilter}, so the CSV always matches the screen.
 */
@RestController
@RequestMapping("/api/finance/loans/disbursements")
@RequiredArgsConstructor
public class FinanceLoanDisbursementController {

    private final LoanDisbursementService disbursementService;
    private final AuthenticationManager authenticationManager;
    private final EmployeeRepository employeeRepository;

    /** Query params map to the filter; status defaults to PENDING_EXTERNAL_PAYMENT. */
    @GetMapping
    public List<LoanDisbursementResponse> list(@ModelAttribute LoanDisbursementFilter filter) {
        requireFinanceAccess();
        return disbursementService.listForFinance(withDefaultStatus(filter));
    }

    /** Exports ALL rows matching the same filters as the list (not one row, not one page). */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@ModelAttribute LoanDisbursementFilter filter) {
        requireFinanceAccess();
        LoanDisbursementFilter f = withDefaultStatus(filter);
        byte[] csv = disbursementService.exportCsvForFinance(f);

        String label = f.getStatus() == LoanDisbursementStatus.PENDING_EXTERNAL_PAYMENT
                ? "pending"
                : f.getStatus().name().toLowerCase(Locale.ROOT);
        String filename = "loan-disbursements-" + label + "-" + LocalDate.now() + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv);
    }

    @PostMapping("/{id}/paid")
    public LoanDisbursementResponse markPaid(@PathVariable Long id,
                                             @RequestBody(required = false) LoanConfirmDisbursementCommand command) {
        requireFinanceAccess();
        LoanConfirmDisbursementCommand cmd = command != null ? command : new LoanConfirmDisbursementCommand();
        cmd.setActorEmployeeId(currentEmployeeId()); // from the logged-in user, never from the body
        return disbursementService.confirmExternalPaid(id, cmd);
    }

    private static LoanDisbursementFilter withDefaultStatus(LoanDisbursementFilter filter) {
        LoanDisbursementFilter f = filter != null ? filter : new LoanDisbursementFilter();
        if (f.getStatus() == null) {
            f.setStatus(LoanDisbursementStatus.PENDING_EXTERNAL_PAYMENT);
        }
        return f;
    }

    /** The list/export service methods have no access check (rows carry bank details), so enforce it here. */
    private void requireFinanceAccess() {
        if (!authenticationManager.isLoanFinanceApprover() && !authenticationManager.isAdmin()) {
            throw new AccessDeniedException("Only Finance or Admin can view loan disbursements.");
        }
    }

    private Long currentEmployeeId() {
        String email = authenticationManager.getCurrentUserEmail();
        if (email == null) {
            throw new IllegalArgumentException("The confirming Finance user could not be identified.");
        }
        // ASSUMPTION: EmployeeRepository exposes findByEmail(String) -> Optional<Employee>.
        // If your lookup differs (e.g. findByWorkEmail), change only this line.
        return employeeRepository.findByEmail(email)
                .map(e -> e.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "The confirming Finance user could not be identified."));
    }
}
