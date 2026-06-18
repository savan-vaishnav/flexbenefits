package com.flexbenefits.service;

import com.flexbenefits.config.TenantContext;
import com.flexbenefits.dto.DocumentResponse;
import com.flexbenefits.entity.Claim;
import com.flexbenefits.entity.ClaimDocument;
import com.flexbenefits.entity.Tenant;
import com.flexbenefits.entity.User;
import com.flexbenefits.exception.ResourceNotFoundException;
import com.flexbenefits.repository.ClaimDocumentRepository;
import com.flexbenefits.repository.ClaimRepository;
import com.flexbenefits.repository.TenantRepository;
import com.flexbenefits.repository.UserRepository;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final MinioClient minioClient;
    private final ClaimDocumentRepository documentRepository;
    private final ClaimRepository claimRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;

    @Value("${minio.bucket}")
    private String bucketName;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB

    /**
     * Upload a document to a claim.
     */
    public DocumentResponse uploadDocument(UUID claimId, MultipartFile file) {
        UUID tenantId = TenantContext.getTenantId();

        // Validate file
        if (file.isEmpty()) {
            throw new IllegalStateException("File is empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalStateException("File size exceeds maximum of 10 MB");
        }

        // Verify claim belongs to tenant
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", claimId));
        if (!claim.getTenant().getId().equals(tenantId)) {
            throw new ResourceNotFoundException("Claim", claimId);
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        // Build storage key: tenantId/claimId/uuid-filename
        String storageKey = tenantId + "/" + claimId + "/" + UUID.randomUUID() + "-" + file.getOriginalFilename();

        try {
            // Upload to MinIO
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(storageKey)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());

            log.info("File uploaded to MinIO: {}", storageKey);
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file to MinIO: " + e.getMessage(), e);
        }

        // Get current user
        User currentUser = getCurrentUser();

        // Save metadata to DB
        ClaimDocument document = ClaimDocument.builder()
                .tenant(tenant)
                .claim(claim)
                .fileName(file.getOriginalFilename())
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .storageKey(storageKey)
                .uploadedBy(currentUser)
                .build();

        ClaimDocument saved = documentRepository.save(document);
        log.info("Document metadata saved: {} for claim: {}", saved.getId(), claimId);

        return toResponse(saved);
    }

    /**
     * Get all documents for a claim.
     */
    @Transactional(readOnly = true)
    public List<DocumentResponse> getDocumentsByClaim(UUID claimId) {
        UUID tenantId = TenantContext.getTenantId();

        // Verify claim belongs to tenant
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", claimId));
        if (!claim.getTenant().getId().equals(tenantId)) {
            throw new ResourceNotFoundException("Claim", claimId);
        }

        return documentRepository.findByClaimIdAndTenantId(claimId, tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Get document metadata by ID.
     */
    @Transactional(readOnly = true)
    public DocumentResponse getDocumentById(UUID documentId) {
        UUID tenantId = TenantContext.getTenantId();

        ClaimDocument document = documentRepository.findByIdAndTenantId(documentId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));

        return toResponse(document);
    }

    /**
     * Download a document's file content from MinIO.
     */
    @Transactional(readOnly = true)
    public InputStream downloadDocument(UUID documentId) {
        UUID tenantId = TenantContext.getTenantId();

        ClaimDocument document = documentRepository.findByIdAndTenantId(documentId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));

        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(document.getStorageKey())
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to download file from MinIO: " + e.getMessage(), e);
        }
    }

    /**
     * Get the content type for download response headers.
     */
    @Transactional(readOnly = true)
    public ClaimDocument getDocumentEntity(UUID documentId) {
        UUID tenantId = TenantContext.getTenantId();
        return documentRepository.findByIdAndTenantId(documentId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
    }

    /**
     * Delete a document (from MinIO + DB).
     */
    public void deleteDocument(UUID documentId) {
        UUID tenantId = TenantContext.getTenantId();

        ClaimDocument document = documentRepository.findByIdAndTenantId(documentId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));

        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(document.getStorageKey())
                    .build());
            log.info("File deleted from MinIO: {}", document.getStorageKey());
        } catch (Exception e) {
            log.error("Failed to delete file from MinIO: {}", e.getMessage());
        }

        documentRepository.delete(document);
        log.info("Document deleted: {}", documentId);
    }

    // --- Private helpers ---

    private User getCurrentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return user;
        }
        return null;
    }

    private DocumentResponse toResponse(ClaimDocument doc) {
        return new DocumentResponse(
                doc.getId(),
                doc.getClaim().getId(),
                doc.getFileName(),
                doc.getContentType(),
                doc.getFileSize(),
                doc.getCreatedAt()
        );
    }
}

