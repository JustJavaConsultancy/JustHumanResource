package com.justjava.humanresource.loan.controller;

import com.justjava.humanresource.loan.dto.LoanProductCommand;
import com.justjava.humanresource.loan.dto.LoanProductResponse;
import com.justjava.humanresource.loan.dto.LoanProductSummaryResponse;
import com.justjava.humanresource.loan.service.LoanProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Loan product setup. Role rules (HR/admin to change, HR/Finance/admin to view all) live in the service. */
@RestController
@RequestMapping("/api/loans/products")
@RequiredArgsConstructor
public class LoanProductController {

    private final LoanProductService productService;

    @GetMapping
    public List<LoanProductResponse> listAll() {
        return productService.listAll();
    }

    @GetMapping("/active")
    public List<LoanProductSummaryResponse> listActive() {
        return productService.listActive();
    }

    @GetMapping("/{id}")
    public LoanProductResponse get(@PathVariable Long id) {
        return productService.getById(id);
    }

    @PostMapping
    public ResponseEntity<LoanProductResponse> create(@Valid @RequestBody LoanProductCommand command) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(command));
    }

    @PutMapping("/{id}")
    public LoanProductResponse update(@PathVariable Long id, @Valid @RequestBody LoanProductCommand command) {
        return productService.update(id, command);
    }

    @PostMapping("/{id}/deactivate")
    public LoanProductResponse deactivate(@PathVariable Long id) {
        return productService.deactivate(id);
    }

    @PostMapping("/{id}/reactivate")
    public LoanProductResponse reactivate(@PathVariable Long id) {
        return productService.reactivate(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        productService.delete(id);
    }
}
