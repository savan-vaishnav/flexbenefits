package com.flexbenefits.controller;

import com.flexbenefits.dto.DocumentResponse;
import com.flexbenefits.entity.ClaimDocument;
import com.flexbenefits.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/claims/{claimId}/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> uploadDocument(
            @PathVariable UUID claimId,
            @RequestParam("file") MultipartFile file) {
        DocumentResponse response = documentService.uploadDocument(claimId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<DocumentResponse>> getDocuments(@PathVariable UUID claimId) {
        List<DocumentResponse> documents = documentService.getDocumentsByClaim(claimId);
        return ResponseEntity.ok(documents);
    }

    @GetMapping("/{documentId}")
    public ResponseEntity<DocumentResponse> getDocument(
            @PathVariable UUID claimId,
            @PathVariable UUID documentId) {
        DocumentResponse response = documentService.getDocumentById(documentId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{documentId}/download")
    public ResponseEntity<InputStreamResource> downloadDocument(
            @PathVariable UUID claimId,
            @PathVariable UUID documentId) {
        ClaimDocument document = documentService.getDocumentEntity(documentId);
        InputStream stream = documentService.downloadDocument(documentId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + document.getFileName() + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(document.getFileSize()))
                .body(new InputStreamResource(stream));
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable UUID claimId,
            @PathVariable UUID documentId) {
        documentService.deleteDocument(documentId);
        return ResponseEntity.noContent().build();
    }
}

