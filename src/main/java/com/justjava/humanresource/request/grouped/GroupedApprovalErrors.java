package com.justjava.humanresource.request.grouped;

import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes={GroupedApprovalController.class, GroupedRequestApprovalController.class})
public class GroupedApprovalErrors {
    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail status(ResponseStatusException error) { return ProblemDetail.forStatusAndDetail(error.getStatusCode(), error.getReason() == null ? "Operation unavailable." : error.getReason()); }
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalid(IllegalArgumentException error) { return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, error.getMessage()); }
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail denied(AccessDeniedException error) { return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, error.getMessage()); }
    @ExceptionHandler(java.util.NoSuchElementException.class)
    ProblemDetail missing() { return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "The requested resource is unavailable."); }
    @ExceptionHandler({org.springframework.dao.PessimisticLockingFailureException.class, org.springframework.orm.ObjectOptimisticLockingFailureException.class})
    ProblemDetail conflict() { return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "This request is being updated or has changed. Refresh its status before retrying."); }
}
