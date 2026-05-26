package com.rooti.domain.document.application;

import com.rooti.domain.caregiver.domain.CaregiverWorkerRelation;
import com.rooti.domain.caregiver.infrastructure.CaregiverWorkerRelationRepository;
import com.rooti.domain.document.domain.CaregiverDocument;
import com.rooti.domain.document.domain.CaregiverDocumentLog;
import com.rooti.domain.document.domain.CaregiverDocumentLog.ActionType;
import com.rooti.domain.document.domain.CaregiverDocumentType;
import com.rooti.domain.document.infrastructure.DocumentRepositories.DocumentLogRepository;
import com.rooti.domain.document.infrastructure.DocumentRepositories.DocumentRepository;
import com.rooti.domain.document.infrastructure.DocumentRepositories.DocumentTypeRepository;
import com.rooti.domain.document.infrastructure.StorageService;
import com.rooti.domain.document.infrastructure.StorageService.Uploaded;
import com.rooti.domain.document.presentation.dto.DocumentDtos.DocumentResponse;
import com.rooti.domain.user.application.UserQueryService;
import com.rooti.domain.user.domain.User;
import com.rooti.global.exception.BusinessException;
import com.rooti.global.exception.ErrorCode;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CaregiverDocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentTypeRepository typeRepository;
    private final DocumentLogRepository logRepository;
    private final CaregiverWorkerRelationRepository relationRepository;
    private final StorageService storageService;
    private final UserQueryService userQueryService;

    public DocumentResponse upload(
            long actorUserId, long relationId, long typeId, MultipartFile file) {
        CaregiverWorkerRelation relation =
                relationRepository
                        .findById(relationId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.CAREGIVER_NOT_FOUND));
        CaregiverDocumentType type =
                typeRepository
                        .findById(typeId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_TYPE_NOT_FOUND));

        Uploaded up = doUpload(file, "caregiver-documents/" + relationId);

        CaregiverDocument doc =
                CaregiverDocument.builder()
                        .relation(relation)
                        .type(type)
                        .filename(up.key())
                        .fileSize(up.size())
                        .contentType(up.contentType())
                        .build();
        documentRepository.save(doc);
        log(actorUserId, doc, ActionType.UPLOAD);
        return DocumentResponse.from(doc, up.url());
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> listByRelation(long relationId) {
        return documentRepository.findAllByRelationId(relationId).stream()
                .map(d -> DocumentResponse.from(d, null))
                .toList();
    }

    public void delete(long actorUserId, long documentId) {
        CaregiverDocument doc =
                documentRepository
                        .findById(documentId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
        log(actorUserId, doc, ActionType.DELETE);
        storageService.delete(doc.getFilename());
        documentRepository.delete(doc);
    }

    public CaregiverDocument loadForDownload(long actorUserId, long documentId) {
        CaregiverDocument doc =
                documentRepository
                        .findById(documentId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
        log(actorUserId, doc, ActionType.DOWNLOAD);
        return doc;
    }

    private void log(long userId, CaregiverDocument doc, ActionType type) {
        User user = userQueryService.getById(userId);
        logRepository.save(CaregiverDocumentLog.of(doc, user, type));
    }

    private Uploaded doUpload(MultipartFile file, String prefix) {
        try {
            return storageService.store(
                    prefix, file.getOriginalFilename(), file.getInputStream(), file.getSize(), file.getContentType());
        } catch (IOException e) {
            log.error("Multipart read failed", e);
            throw new BusinessException(ErrorCode.STORAGE_UPLOAD_FAILED);
        }
    }
}
