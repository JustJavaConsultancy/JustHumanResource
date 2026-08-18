package com.justjava.humanresource.payroll.statutory.service.impl;

import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.payroll.statutory.dto.TaxBandUploadRowDTO;
import com.justjava.humanresource.payroll.statutory.entity.PayeTaxBand;
import com.justjava.humanresource.payroll.statutory.repositories.PayeTaxBandRepository;
import com.justjava.humanresource.payroll.statutory.service.TaxBandCsvParserService;
import com.justjava.humanresource.payroll.statutory.service.TaxBandUploadService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TaxBandUploadServiceImpl implements TaxBandUploadService {

    private final TaxBandCsvParserService csvParserService;
    private final PayeTaxBandRepository payeTaxBandRepository;

    @Override
    @Transactional
    public UploadSummary uploadTaxBands(MultipartFile file) {

        List<TaxBandUploadRowDTO> rows = csvParserService.parse(file);
        List<RowError> errors = new ArrayList<>();

        if (rows.isEmpty()) {
            errors.add(new RowError(0, "CSV file contains no data rows"));
            throw new TaxBandUploadValidationException(0, errors);
        }

        LocalDate effectiveFrom = LocalDate.now();

        // ---- 1. Per-row field validation ----
        for (TaxBandUploadRowDTO row : rows) {
            if (row.getLowerBound() == null) {
                errors.add(new RowError(row.getRowNumber(), "Lower bound is required"));
                continue;
            }
            if (row.getLowerBound().compareTo(BigDecimal.ZERO) < 0) {
                errors.add(new RowError(row.getRowNumber(), "Lower bound cannot be negative"));
            }
            if (row.getRate() == null) {
                errors.add(new RowError(row.getRowNumber(), "Rate is required"));
            } else if (row.getRate().compareTo(BigDecimal.ZERO) < 0
                    || row.getRate().compareTo(BigDecimal.valueOf(100)) > 0) {
                errors.add(new RowError(row.getRowNumber(), "Rate must be between 0 and 100"));
            }
            if (row.getUpperBound() != null
                    && row.getLowerBound() != null
                    && row.getUpperBound().compareTo(row.getLowerBound()) <= 0) {
                errors.add(new RowError(row.getRowNumber(), "Upper bound must be greater than lower bound"));
            }
        }

        // ---- 2. Contiguity check (only if no field errors so far, to avoid noisy output) ----
        if (errors.isEmpty()) {
            for (int i = 0; i < rows.size() - 1; i++) {
                TaxBandUploadRowDTO current = rows.get(i);
                TaxBandUploadRowDTO next = rows.get(i + 1);

                if (current.getUpperBound() == null) {
                    errors.add(new RowError(current.getRowNumber(),
                            "Only the last row may have an open-ended (blank) upper bound"));
                    continue;
                }
                if (current.getUpperBound().compareTo(next.getLowerBound()) < 0) {
                    errors.add(new RowError(next.getRowNumber(),
                            "Gap detected: previous band ends at " + current.getUpperBound()
                                    + " but this band starts at " + next.getLowerBound()));
                }
            }
        }

        // ---- 3. Duplicate lowerBound within the same file ----
        Map<BigDecimal, Integer> seenLowerBounds = new HashMap<>();
        for (TaxBandUploadRowDTO row : rows) {
            if (row.getLowerBound() == null) continue;
            Integer firstRow = seenLowerBounds.putIfAbsent(row.getLowerBound(), row.getRowNumber());
            if (firstRow != null) {
                errors.add(new RowError(row.getRowNumber(),
                        "Duplicate lower bound " + row.getLowerBound() + " (already used in row " + firstRow + ")"));
            }
        }

        // ---- 4. (Removed) Previously checked for conflicts against existing DB rows.
        // No longer needed: every successful upload now wipes and replaces all
        // existing tax bands, so there is nothing left to conflict with.

        if (!errors.isEmpty()) {
            throw new TaxBandUploadValidationException(rows.size(), errors);
        }

        // ---- 5. Full replace: wipe every existing tax band, then insert the new set ----
        // (Only reached once validation above has fully passed, so a bad CSV
        // never deletes existing data — this whole method runs in one transaction.)
        payeTaxBandRepository.deleteAllInBatch();

        List<String> generatedRegimeCodes = new ArrayList<>();

        for (TaxBandUploadRowDTO row : rows) {
            String regimeCode = generateRegimeCode();
            generatedRegimeCodes.add(regimeCode);

            PayeTaxBand band = new PayeTaxBand();
            band.setLowerBound(row.getLowerBound());
            band.setUpperBound(row.getUpperBound());
            band.setRate(row.getRate());
            band.setEffectiveFrom(effectiveFrom);
            band.setEffectiveTo(null);
            band.setStatus(RecordStatus.ACTIVE);
            band.setRegimeCode(regimeCode);
            payeTaxBandRepository.save(band);
        }

        return new UploadSummary(rows.size(), rows.size(), String.join(", ", generatedRegimeCodes));
    }

    private String generateRegimeCode() {
        String suffix = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return "PAYE-BULK-" + suffix;
    }

    public record RowError(int rowNumber, String message) {}

    @Getter
    public static class TaxBandUploadValidationException extends RuntimeException {
        private final int totalRows;
        private final List<RowError> rowErrors;

        public TaxBandUploadValidationException(int totalRows, List<RowError> rowErrors) {
            super("Tax band upload blocked due to validation errors");
            this.totalRows = totalRows;
            this.rowErrors = rowErrors;
        }
    }
}