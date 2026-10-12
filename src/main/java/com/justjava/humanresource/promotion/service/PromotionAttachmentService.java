package com.justjava.humanresource.promotion.service;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.promotion.dto.PromotionAttachmentResponse;
import com.justjava.humanresource.promotion.entity.PromotionAttachment;
import com.justjava.humanresource.promotion.entity.PromotionRequest;
import com.justjava.humanresource.promotion.enums.PromotionActivityType;
import com.justjava.humanresource.promotion.enums.PromotionAttachmentType;
import com.justjava.humanresource.promotion.repository.PromotionAttachmentRepository;
import com.justjava.humanresource.promotion.repository.PromotionRequestRepository;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PromotionAttachmentService {

    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/png",
            "image/jpeg",
            "text/plain",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private final PromotionAttachmentRepository attachmentRepository;
    private final PromotionRequestRepository requestRepository;
    private final PromotionAuthorizationService authorizationService;
    private final PromotionCurrentUserService currentUserService;
    private final PromotionActivityService activityService;
    private final PromotionLetterTemplateService letterTemplateService;
    private final PromotionDocumentIntegrationService documentIntegrationService;

    @Value("${app.promotions.storage-path:${user.home}/just-hr/promotion-attachments}")
    private String storageRoot;

    @Transactional(readOnly = true)
    public List<PromotionAttachmentResponse> list(Long requestId) {
        PromotionRequest request = request(requestId);
        authorizationService.requireCanView(request);
        return attachmentRepository.findByPromotionRequest_IdOrderByUploadedAtDesc(requestId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    @PreAuthorize("@promotionSecurity.canManagePromotions()")
    public PromotionAttachmentResponse upload(Long requestId, MultipartFile file,
                                              PromotionAttachmentType documentType,
                                              boolean required) {
        authorizationService.requireHrOrAdmin();
        PromotionRequest request = request(requestId);
        validateFile(file);
        Employee actor = currentUserService.getCurrentEmployeeOrSystem();
        String original = Paths.get(Optional.ofNullable(file.getOriginalFilename()).orElse("file"))
                .getFileName()
                .toString();
        String stored = UUID.randomUUID() + extension(original);
        try {
            Path root = Paths.get(storageRoot).toAbsolutePath().normalize();
            Path dir = root.resolve(String.valueOf(requestId)).normalize();
            if (!dir.startsWith(root)) {
                throw new IllegalStateException("Invalid promotion attachment path.");
            }
            Files.createDirectories(dir);
            Path target = dir.resolve(stored).normalize();
            try (var input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }

            PromotionAttachment attachment = new PromotionAttachment();
            attachment.setPromotionRequest(request);
            attachment.setDocumentType(documentType == null ? PromotionAttachmentType.OTHER : documentType);
            attachment.setFileName(original);
            attachment.setContentType(Optional.ofNullable(file.getContentType()).orElse("application/octet-stream"));
            attachment.setStorageReference(target.toString());
            attachment.setFileSize(file.getSize());
            attachment.setUploadedBy(actor);
            attachment.setUploadedAt(LocalDateTime.now());
            attachment.setRequired(required);
            attachment = attachmentRepository.save(attachment);
            documentIntegrationService.syncToEmployeeDocumentLibrary(attachment);
            attachment = attachmentRepository.save(attachment);
            activityService.record(request, PromotionActivityType.DOCUMENT_UPLOADED, actor,
                    "Promotion document uploaded: " + original);
            return toResponse(attachment);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not store promotion attachment.", ex);
        }
    }

    @Transactional
    @PreAuthorize("@promotionSecurity.canManagePromotions()")
    public PromotionAttachmentResponse generateLetter(Long requestId, PromotionAttachmentType documentType) {
        authorizationService.requireHrOrAdmin();
        PromotionRequest request = request(requestId);
        PromotionAttachmentType type = documentType == null
                ? PromotionAttachmentType.PROMOTION_LETTER
                : documentType;
        PromotionLetterTemplateService.RenderedTemplate template = letterTemplateService.render(request, type);
        Employee actor = currentUserService.getCurrentEmployeeOrSystem();
        String fileName = generatedFileName(request, type);
        try {
            Path dir = requestDirectory(requestId);
            Files.createDirectories(dir);
            Path target = dir.resolve(UUID.randomUUID() + ".pdf").normalize();
            writePdf(target, template.getLines());

            PromotionAttachment attachment = new PromotionAttachment();
            attachment.setPromotionRequest(request);
            attachment.setDocumentType(type);
            attachment.setFileName(fileName);
            attachment.setContentType("application/pdf");
            attachment.setStorageReference(target.toString());
            attachment.setFileSize(Files.size(target));
            attachment.setGeneratedTemplateKey(template.getKey());
            attachment.setGeneratedTemplateName(template.getName());
            attachment.setGeneratedTemplateVersion(template.getVersion());
            attachment.setUploadedBy(actor);
            attachment.setUploadedAt(LocalDateTime.now());
            attachment.setRequired(false);
            attachment = attachmentRepository.save(attachment);
            documentIntegrationService.syncToEmployeeDocumentLibrary(attachment);
            attachment = attachmentRepository.save(attachment);
            activityService.record(request, PromotionActivityType.DOCUMENT_UPLOADED, actor,
                    "Promotion document generated: " + fileName);
            return toResponse(attachment);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not generate promotion letter.", ex);
        }
    }

    @Transactional(readOnly = true)
    public Resource download(Long requestId, Long attachmentId) {
        PromotionRequest request = request(requestId);
        authorizationService.requireCanView(request);
        PromotionAttachment attachment = attachment(requestId, attachmentId);
        Resource resource = new FileSystemResource(Paths.get(attachment.getStorageReference()).normalize());
        if (!resource.exists()) {
            throw new IllegalStateException("Promotion attachment file is missing.");
        }
        return resource;
    }

    @Transactional(readOnly = true)
    public PromotionAttachment get(Long requestId, Long attachmentId) {
        request(requestId);
        return attachment(requestId, attachmentId);
    }

    @Transactional
    @PreAuthorize("@promotionSecurity.canManagePromotions()")
    public void delete(Long requestId, Long attachmentId) {
        authorizationService.requireHrOrAdmin();
        PromotionRequest request = request(requestId);
        PromotionAttachment attachment = attachment(requestId, attachmentId);
        try {
            Files.deleteIfExists(Paths.get(attachment.getStorageReference()).normalize());
        } catch (IOException ex) {
            throw new IllegalStateException("Could not delete promotion attachment.", ex);
        }
        attachmentRepository.delete(attachment);
        activityService.record(request, PromotionActivityType.DOCUMENT_REMOVED,
                currentUserService.getCurrentEmployeeOrSystem(),
                "Promotion document removed: " + attachment.getFileName());
    }

    private PromotionRequest request(Long requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Promotion request not found: " + requestId));
    }

    private PromotionAttachment attachment(Long requestId, Long attachmentId) {
        PromotionAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("Promotion attachment not found: " + attachmentId));
        if (!attachment.getPromotionRequest().getId().equals(requestId)) {
            throw new IllegalArgumentException("Promotion attachment does not belong to this request.");
        }
        return attachment;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A non-empty file is required.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File exceeds the 20 MB limit.");
        }
        String contentType = Optional.ofNullable(file.getContentType()).orElse("application/octet-stream");
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Unsupported file type: " + contentType);
        }
    }

    private Path requestDirectory(Long requestId) {
        Path root = Paths.get(storageRoot).toAbsolutePath().normalize();
        Path dir = root.resolve(String.valueOf(requestId)).normalize();
        if (!dir.startsWith(root)) {
            throw new IllegalStateException("Invalid promotion attachment path.");
        }
        return dir;
    }

    private void writePdf(Path target, List<String> lines) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(PDType1Font.HELVETICA_BOLD, 16);
                content.newLineAtOffset(72, 720);
                content.showText(lines.getFirst());
                content.setFont(PDType1Font.HELVETICA, 11);
                content.setLeading(18);
                for (String line : lines.subList(1, lines.size())) {
                    content.newLine();
                    content.showText(pdfText(line));
                }
                content.endText();
            }
            document.save(target.toFile());
        }
    }

    private String pdfText(String value) {
        return value == null ? "N/A" : value;
    }

    private String generatedFileName(PromotionRequest request, PromotionAttachmentType type) {
        String requestNumber = Optional.ofNullable(request.getRequestNumber()).orElse("promotion-request");
        String suffix = type == PromotionAttachmentType.PROMOTION_LETTER
                ? "promotion-letter"
                : "salary-adjustment-letter";
        return requestNumber.replaceAll("[^A-Za-z0-9._-]", "_") + "-" + suffix + ".pdf";
    }

    private PromotionAttachmentResponse toResponse(PromotionAttachment attachment) {
        Employee uploadedBy = attachment.getUploadedBy();
        return PromotionAttachmentResponse.builder()
                .id(attachment.getId())
                .documentType(attachment.getDocumentType())
                .fileName(attachment.getFileName())
                .contentType(attachment.getContentType())
                .fileSize(attachment.getFileSize())
                .generatedTemplateKey(attachment.getGeneratedTemplateKey())
                .generatedTemplateName(attachment.getGeneratedTemplateName())
                .generatedTemplateVersion(attachment.getGeneratedTemplateVersion())
                .documentLibraryDocumentId(attachment.getDocumentLibraryDocumentId())
                .documentLibrarySyncedAt(attachment.getDocumentLibrarySyncedAt())
                .documentLibrarySyncError(attachment.getDocumentLibrarySyncError())
                .uploadedByName(uploadedBy == null ? "System" : uploadedBy.getFullName())
                .uploadedAt(attachment.getUploadedAt())
                .required(attachment.isRequired())
                .build();
    }

    private String extension(String name) {
        int index = name.lastIndexOf('.');
        if (index < 0) {
            return "";
        }
        String extension = name.substring(index).toLowerCase(Locale.ROOT);
        return extension.length() <= 10 ? extension : "";
    }
}
