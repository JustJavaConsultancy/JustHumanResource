package com.justjava.humanresource.integration.inbound.payroll.controller;

import com.justjava.humanresource.integration.inbound.payroll.controller.PayrollJournalIntegrationController;
import com.justjava.humanresource.integration.inbound.payroll.dto.PayrollJournalErrorResponse;
import com.justjava.humanresource.integration.inbound.payroll.exception.IntegrationResourceNotFoundException;

import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;




@RestControllerAdvice(assignableTypes = PayrollJournalIntegrationController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PayrollJournalIntegrationExceptionHandler {
    @ExceptionHandler(IntegrationResourceNotFoundException.class)
    ResponseEntity<PayrollJournalErrorResponse> notFound() {
        return response(HttpStatus.NOT_FOUND, "NOT_FOUND", "Journal reference was not found.", false);
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<PayrollJournalErrorResponse> conflict() {
        return response(HttpStatus.CONFLICT, "INVALID_TRANSITION",
                "The accounting status transition is not allowed.", false);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, IllegalArgumentException.class})
    ResponseEntity<PayrollJournalErrorResponse> badRequest(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The integration request is invalid.", false);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<PayrollJournalErrorResponse> unreadableBody() {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The integration request body is invalid.", false);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<PayrollJournalErrorResponse> unsupportedMediaType() {
        return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "The integration request must use application/json.", false);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<PayrollJournalErrorResponse> internalError() {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "The integration request could not be completed.", true);
    }

    private ResponseEntity<PayrollJournalErrorResponse> response(
            HttpStatus status, String code, String message, boolean retryable) {
        return ResponseEntity.status(status).body(
                new PayrollJournalErrorResponse(code, message, retryable, List.of()));
    }
}
