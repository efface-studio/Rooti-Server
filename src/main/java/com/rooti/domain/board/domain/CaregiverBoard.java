package com.rooti.domain.board.domain;

import com.rooti.domain.user.domain.User;
import com.rooti.global.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Caregiver community post. Body is sanitized HTML (server-side via JSoup whitelist). */
@Entity
@Table(name = "caregiver_boards")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CaregiverBoard extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "is_published", nullable = false)
    private boolean published;

    @Builder
    private CaregiverBoard(User author, String title, String body, Boolean published) {
        this.author = author;
        this.title = title;
        this.body = body;
        this.published = published == null || published;
    }

    public void edit(String title, String body) {
        if (title != null && !title.isBlank()) this.title = title;
        if (body != null) this.body = body;
    }

    public void publish() {
        this.published = true;
    }

    public void unpublish() {
        this.published = false;
    }
}
