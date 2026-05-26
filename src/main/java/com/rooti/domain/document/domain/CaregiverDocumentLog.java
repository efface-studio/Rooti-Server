package com.rooti.domain.document.domain;

import com.rooti.domain.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "caregiver_document_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CaregiverDocumentLog {

    public enum ActionType {
        UPLOAD,
        DOWNLOAD,
        DELETE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private CaregiverDocument document;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 20)
    private ActionType actionType;

    @Column(name = "action_at", nullable = false)
    private LocalDateTime actionAt;

    public static CaregiverDocumentLog of(CaregiverDocument doc, User user, ActionType type) {
        CaregiverDocumentLog log = new CaregiverDocumentLog();
        log.document = doc;
        log.user = user;
        log.actionType = type;
        log.actionAt = LocalDateTime.now();
        return log;
    }
}
