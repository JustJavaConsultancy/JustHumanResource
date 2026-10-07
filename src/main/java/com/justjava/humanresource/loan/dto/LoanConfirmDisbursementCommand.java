package com.justjava.humanresource.loan.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Finance "mark as paid" input. Deliberately carries no loan terms: Finance confirms payment only.
 */
@Getter
@Setter
@NoArgsConstructor
public class LoanConfirmDisbursementCommand {

    /** Optional. Defaults to today. Cannot be in the future or before final approval. */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate paidDate;

    /** Optional, max 100 characters. */
    private String paymentReference;

    /** Optional, max 2000 characters. */
    private String comment;

    /**
     * Employee id of the Finance user confirming payment. Set by the controller from the logged-in
     * user, never read from the request body.
     */
    @JsonIgnore
    private Long actorEmployeeId;
}
