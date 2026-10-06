package com.justjava.humanresource.loan.service.impl;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.loan.dto.LoanAttachmentResponse;
import com.justjava.humanresource.loan.entity.EmployeeLoanApplication;
import com.justjava.humanresource.loan.entity.EmployeeLoanAttachment;
import com.justjava.humanresource.loan.enums.LoanAttachmentType;
import com.justjava.humanresource.loan.repository.EmployeeLoanApplicationRepository;
import com.justjava.humanresource.loan.repository.EmployeeLoanAttachmentRepository;
import com.justjava.humanresource.loan.service.LoanAttachmentService;
import com.justjava.humanresource.loan.service.LoanEmployeeContextService;
import com.justjava.humanresource.loan.service.LoanEmployeeContextService.ViewerRole;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoanAttachmentServiceImpl implements LoanAttachmentService {

    private static final long MAX_BYTES = 20L * 1024 * 1024;
    private static final Set<String> ALLOWED = Set.of(
            "application/pdf", "image/png", "image/jpeg", "text/plain",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final EmployeeLoanAttachmentRepository attachmentRepository;
    private final EmployeeLoanApplicationRepository applicationRepository;
    private final LoanEmployeeContextService contextService;

    @Value("${app.loans.storage-path:${user.home}/just-hr/loan-attachments}")
    private String storageRoot;

    // ------------------------------------------------------------------ commands

    @Override
    @Transactional
    public LoanAttachmentResponse upload(Long loanApplicationId, MultipartFile file, LoanAttachmentType type) {
        Employee me = contextService.getCurrentEmployee();
        EmployeeLoanApplication application = loadOwned(loanApplicationId, me.getId());
        if (!LoanApplicationSupport.EDITABLE.contains(application.getStatus())) {
            throw new IllegalStateException("Attachments can only be added while the application is a draft "
                    + "or returned for correction.");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A non-empty file is required.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("File exceeds the 20 MB limit.");
        }
        String contentType = Optional.ofNullable(file.getContentType()).orElse("application/octet-stream");
        if (!ALLOWED.contains(contentType)) {
            throw new IllegalArgumentException("Unsupported file type: " + contentType);
        }

        String original = sanitizeFilename(file.getOriginalFilename());
        Path root = root();
        Path dir = root.resolve(String.valueOf(loanApplicationId)).normalize();
        if (!dir.startsWith(root)) {
            throw new IllegalStateException("Invalid storage path.");
        }
        Path target = dir.resolve(UUID.randomUUID() + extension(original)).normalize();

        try {
            Files.createDirectories(dir);
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Could not store attachment.", ex);
        }

        try {
            EmployeeLoanAttachment attachment = new EmployeeLoanAttachment();
            attachment.setLoanApplicationId(loanApplicationId);
            attachment.setAttachmentType(type == null ? LoanAttachmentType.SUPPORTING_DOCUMENT : type);
            attachment.setOriginalFilename(original);
            attachment.setContentType(contentType);
            attachment.setFileSize(file.getSize());
            attachment.setStoragePath(target.toString());
            attachment.setUploadedByEmployeeId(me.getId());
            attachment = attachmentRepository.save(attachment);
            return toResponse(attachment, Map.of(me.getId(), me.getFullName()), true);
        } catch (RuntimeException ex) {
            deleteFileQuietly(target);   // do not leave an orphan file behind a failed save
            throw ex;
        }
    }

    @Override
    @Transactional
    public void delete(Long loanApplicationId, Long attachmentId) {
        Employee me = contextService.getCurrentEmployee();
        EmployeeLoanApplication application = loadOwned(loanApplicationId, me.getId());
        if (!LoanApplicationSupport.EDITABLE.contains(application.getStatus())) {
            throw new IllegalStateException("Attachments can only be removed while the application is a draft "
                    + "or returned for correction.");
        }
        EmployeeLoanAttachment attachment = getForApplication(loanApplicationId, attachmentId);
        attachmentRepository.delete(attachment);
        deleteFileQuietly(Paths.get(attachment.getStoragePath()));
    }

    @Override
    @Transactional
    public void deleteAllForApplication(Long loanApplicationId) {
        List<EmployeeLoanAttachment> rows =
                attachmentRepository.findByLoanApplicationIdOrderByCreatedAtAsc(loanApplicationId);
        attachmentRepository.deleteAll(rows);
        rows.forEach(a -> deleteFileQuietly(Paths.get(a.getStoragePath())));
    }

    // ------------------------------------------------------------------ queries

    @Override
    public LoanAttachmentDownload download(Long loanApplicationId, Long attachmentId) {
        EmployeeLoanApplication application = loadAny(loanApplicationId);
        contextService.requireViewAccess(application);
        EmployeeLoanAttachment attachment = getForApplication(loanApplicationId, attachmentId);

        Path path = Paths.get(attachment.getStoragePath()).toAbsolutePath().normalize();
        if (!path.startsWith(root())) {
            throw new IllegalStateException("Invalid storage path.");
        }
        Resource resource = new FileSystemResource(path);
        if (!resource.exists()) {
            throw new IllegalStateException("Attachment file is missing.");
        }
        return new LoanAttachmentDownload(resource, attachment.getOriginalFilename(),
                attachment.getContentType() == null ? "application/octet-stream" : attachment.getContentType());
    }

    @Override
    public List<LoanAttachmentResponse> list(Long loanApplicationId) {
        EmployeeLoanApplication application = loadAny(loanApplicationId);
        ViewerRole role = contextService.requireViewAccess(application);
        boolean canDelete = role == ViewerRole.EMPLOYEE
                && LoanApplicationSupport.EDITABLE.contains(application.getStatus());

        List<EmployeeLoanAttachment> rows =
                attachmentRepository.findByLoanApplicationIdOrderByCreatedAtAsc(loanApplicationId);
        Map<Long, String> names = contextService.employeeNames(
                rows.stream().map(EmployeeLoanAttachment::getUploadedByEmployeeId).collect(Collectors.toSet()));
        return rows.stream().map(a -> toResponse(a, names, canDelete)).toList();
    }

    // ------------------------------------------------------------------ helpers

    private LoanAttachmentResponse toResponse(EmployeeLoanAttachment a, Map<Long, String> names, boolean deletable) {
        return LoanAttachmentResponse.builder()
                .id(a.getId())
                .loanApplicationId(a.getLoanApplicationId())
                .originalFilename(a.getOriginalFilename())
                .contentType(a.getContentType())
                .fileSize(a.getFileSize())
                .attachmentType(a.getAttachmentType())
                .uploadedByEmployeeId(a.getUploadedByEmployeeId())
                .uploadedByName(names.get(a.getUploadedByEmployeeId()))
                .deletable(deletable)
                .createdAt(a.getCreatedAt())
                .build();
    }

    private EmployeeLoanApplication loadOwned(Long applicationId, Long employeeId) {
        return applicationRepository.findByIdAndEmployeeId(applicationId, employeeId)
                .orElseThrow(() -> new EntityNotFoundException("Loan application not found: " + applicationId));
    }

    private EmployeeLoanApplication loadAny(Long applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new EntityNotFoundException("Loan application not found: " + applicationId));
    }

    private EmployeeLoanAttachment getForApplication(Long applicationId, Long attachmentId) {
        EmployeeLoanAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new EntityNotFoundException("Attachment not found: " + attachmentId));
        if (!attachment.getLoanApplicationId().equals(applicationId)) {
            throw new IllegalArgumentException("Attachment does not belong to this loan application.");
        }
        return attachment;
    }

    private Path root() {
        return Paths.get(storageRoot).toAbsolutePath().normalize();
    }

    private static String sanitizeFilename(String name) {
        String cleaned = name == null ? "file" : name.replaceAll(".*[\\\\/]", "").trim();
        return cleaned.isEmpty() ? "file" : cleaned;
    }

    private static String extension(String name) {
        int i = name.lastIndexOf('.');
        if (i < 0) return "";
        String ext = name.substring(i).toLowerCase(Locale.ROOT);
        return ext.length() <= 10 ? ext : "";
    }

    private void deleteFileQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("Could not delete loan attachment file {}", path, ex);
        }
    }
}
