package com.justjava.humanresource.communication.service;

import com.justjava.humanresource.communication.entity.ChatMessage;
import com.justjava.humanresource.communication.entity.ChatMessageAttachment;
import com.justjava.humanresource.communication.entity.GroupChatMessage;
import com.justjava.humanresource.communication.entity.GroupChatMessageAttachment;
import com.justjava.humanresource.communication.entity.HrBroadcast;
import com.justjava.humanresource.communication.entity.HrBroadcastAttachment;
import com.justjava.humanresource.communication.repository.ChatMessageAttachmentRepository;
import com.justjava.humanresource.communication.repository.GroupChatMessageAttachmentRepository;
import com.justjava.humanresource.communication.repository.HrBroadcastAttachmentRepository;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class CommunicationAttachmentService {

    private static final int MAX_ATTACHMENTS = 5;
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private static final Set<String> ALLOWED = Set.of(
            "application/pdf",
            "image/png",
            "image/jpeg",
            "text/plain",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private final ChatMessageAttachmentRepository directAttachmentRepository;
    private final GroupChatMessageAttachmentRepository groupAttachmentRepository;
    private final HrBroadcastAttachmentRepository broadcastAttachmentRepository;

    @Value("${app.communication.storage-path:${user.home}/just-hr/communication-attachments}")
    private String storageRoot;

    public CommunicationAttachmentService(ChatMessageAttachmentRepository directAttachmentRepository,
                                          GroupChatMessageAttachmentRepository groupAttachmentRepository,
                                          HrBroadcastAttachmentRepository broadcastAttachmentRepository) {
        this.directAttachmentRepository = directAttachmentRepository;
        this.groupAttachmentRepository = groupAttachmentRepository;
        this.broadcastAttachmentRepository = broadcastAttachmentRepository;
    }

    @Transactional
    public List<ChatMessageAttachment> storeDirectAttachments(ChatMessage message, List<MultipartFile> files, Long actorId) {
        return validFiles(files).stream()
                .map(file -> storeDirectAttachment(message, file, actorId))
                .toList();
    }

    @Transactional
    public ChatMessageAttachment storeDirectAttachmentCopy(ChatMessage message,
                                                           String originalFilename,
                                                           String contentType,
                                                           Long fileSize,
                                                           Long actorId,
                                                           byte[] bytes) {
        StoredFile storedFile = storeFile("direct", message.getId(), originalFilename, contentType, fileSize, bytes);
        ChatMessageAttachment attachment = new ChatMessageAttachment();
        attachment.setMessage(message);
        attachment.setOriginalFilename(storedFile.originalFilename());
        attachment.setStoredFilename(storedFile.storedFilename());
        attachment.setStoragePath(storedFile.storagePath());
        attachment.setContentType(storedFile.contentType());
        attachment.setFileSize(storedFile.fileSize());
        attachment.setUploadedByEmployeeId(actorId);
        attachment.setUploadedAt(LocalDateTime.now());
        return directAttachmentRepository.save(attachment);
    }

    @Transactional
    public List<GroupChatMessageAttachment> storeGroupAttachments(GroupChatMessage message, List<MultipartFile> files, Long actorId) {
        return validFiles(files).stream()
                .map(file -> storeGroupAttachment(message, file, actorId))
                .toList();
    }

    @Transactional
    public List<HrBroadcastAttachment> storeBroadcastAttachments(HrBroadcast broadcast, List<MultipartFile> files, String actorEmail) {
        return validFiles(files).stream()
                .map(file -> storeBroadcastAttachment(broadcast, file, actorEmail))
                .toList();
    }

    @Transactional(readOnly = true)
    public ChatMessageAttachment getDirectAttachment(Long messageId, Long attachmentId) {
        ChatMessageAttachment attachment = directAttachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found"));
        if (!attachment.getMessage().getId().equals(messageId)) {
            throw new IllegalArgumentException("Attachment does not belong to this message");
        }
        return attachment;
    }

    @Transactional(readOnly = true)
    public GroupChatMessageAttachment getGroupAttachment(Long messageId, Long attachmentId) {
        GroupChatMessageAttachment attachment = groupAttachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found"));
        if (!attachment.getMessage().getId().equals(messageId)) {
            throw new IllegalArgumentException("Attachment does not belong to this message");
        }
        return attachment;
    }

    @Transactional(readOnly = true)
    public HrBroadcastAttachment getBroadcastAttachment(Long broadcastId, Long attachmentId) {
        HrBroadcastAttachment attachment = broadcastAttachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found"));
        if (!attachment.getBroadcast().getId().equals(broadcastId)) {
            throw new IllegalArgumentException("Attachment does not belong to this broadcast");
        }
        return attachment;
    }

    @Transactional(readOnly = true)
    public Resource load(String storagePath) {
        Resource resource = new FileSystemResource(Paths.get(storagePath).normalize());
        if (!resource.exists()) {
            throw new IllegalStateException("Attachment file is missing");
        }
        return resource;
    }

    private ChatMessageAttachment storeDirectAttachment(ChatMessage message, MultipartFile file, Long actorId) {
        StoredFile storedFile = storeFile("direct", message.getId(), file);
        ChatMessageAttachment attachment = new ChatMessageAttachment();
        attachment.setMessage(message);
        attachment.setOriginalFilename(storedFile.originalFilename());
        attachment.setStoredFilename(storedFile.storedFilename());
        attachment.setStoragePath(storedFile.storagePath());
        attachment.setContentType(storedFile.contentType());
        attachment.setFileSize(storedFile.fileSize());
        attachment.setUploadedByEmployeeId(actorId);
        attachment.setUploadedAt(LocalDateTime.now());
        return directAttachmentRepository.save(attachment);
    }

    private GroupChatMessageAttachment storeGroupAttachment(GroupChatMessage message, MultipartFile file, Long actorId) {
        StoredFile storedFile = storeFile("groups", message.getId(), file);
        GroupChatMessageAttachment attachment = new GroupChatMessageAttachment();
        attachment.setMessage(message);
        attachment.setOriginalFilename(storedFile.originalFilename());
        attachment.setStoredFilename(storedFile.storedFilename());
        attachment.setStoragePath(storedFile.storagePath());
        attachment.setContentType(storedFile.contentType());
        attachment.setFileSize(storedFile.fileSize());
        attachment.setUploadedByEmployeeId(actorId);
        attachment.setUploadedAt(LocalDateTime.now());
        return groupAttachmentRepository.save(attachment);
    }

    private HrBroadcastAttachment storeBroadcastAttachment(HrBroadcast broadcast, MultipartFile file, String actorEmail) {
        StoredFile storedFile = storeFile("broadcasts", broadcast.getId(), file);
        HrBroadcastAttachment attachment = new HrBroadcastAttachment();
        attachment.setBroadcast(broadcast);
        attachment.setOriginalFilename(storedFile.originalFilename());
        attachment.setStoredFilename(storedFile.storedFilename());
        attachment.setStoragePath(storedFile.storagePath());
        attachment.setContentType(storedFile.contentType());
        attachment.setFileSize(storedFile.fileSize());
        attachment.setUploadedByEmail(actorEmail);
        attachment.setUploadedAt(LocalDateTime.now());
        return broadcastAttachmentRepository.save(attachment);
    }

    private List<MultipartFile> validFiles(List<MultipartFile> files) {
        List<MultipartFile> valid = files == null ? List.of() : files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .toList();
        if (valid.size() > MAX_ATTACHMENTS) {
            throw new IllegalArgumentException("A message can include up to " + MAX_ATTACHMENTS + " attachments");
        }
        valid.forEach(this::validateFile);
        return valid;
    }

    private void validateFile(MultipartFile file) {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File exceeds the 20 MB limit");
        }
        String contentType = Optional.ofNullable(file.getContentType()).orElse("application/octet-stream");
        if (!ALLOWED.contains(contentType)) {
            throw new IllegalArgumentException("Unsupported file type: " + contentType);
        }
    }

    private StoredFile storeFile(String scope, Long messageId, MultipartFile file) {
        String contentType = Optional.ofNullable(file.getContentType()).orElse("application/octet-stream");
        String originalFilename = Paths.get(Optional.ofNullable(file.getOriginalFilename()).orElse("file"))
                .getFileName()
                .toString();
        try {
            return storeFile(scope, messageId, originalFilename, contentType, file.getSize(), file.getBytes());
        } catch (IOException ex) {
            throw new IllegalStateException("Could not store attachment", ex);
        }
    }

    private StoredFile storeFile(String scope,
                                 Long messageId,
                                 String filename,
                                 String contentType,
                                 Long fileSize,
                                 byte[] bytes) {
        byte[] content = bytes == null ? new byte[0] : bytes;
        String originalFilename = Paths.get(Optional.ofNullable(filename).orElse("file"))
                .getFileName()
                .toString();
        String storedFilename = UUID.randomUUID() + extension(originalFilename);
        String resolvedContentType = Optional.ofNullable(contentType).orElse("application/octet-stream");
        long resolvedFileSize = fileSize == null ? content.length : fileSize;

        try {
            Path root = Paths.get(storageRoot).toAbsolutePath().normalize();
            Path directory = root.resolve(scope).resolve(String.valueOf(messageId)).normalize();
            if (!directory.startsWith(root)) {
                throw new IllegalStateException("Invalid storage path");
            }
            Files.createDirectories(directory);
            Path target = directory.resolve(storedFilename).normalize();
            if (!target.startsWith(directory)) {
                throw new IllegalStateException("Invalid attachment filename");
            }
            Files.write(target, content);
            return new StoredFile(originalFilename, storedFilename, target.toString(), resolvedContentType, resolvedFileSize);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not store attachment", ex);
        }
    }

    private String extension(String filename) {
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            return "";
        }
        return filename.substring(index);
    }

    private record StoredFile(
            String originalFilename,
            String storedFilename,
            String storagePath,
            String contentType,
            Long fileSize
    ) {
    }
}
