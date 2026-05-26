package com.rooti.domain.worker.domain;

import com.rooti.domain.user.domain.User;
import com.rooti.global.audit.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A worker with a disability. 1:1 with a {@link User} of role {@code WORKER}.
 *
 * <p>Identity-bearing personal data (name, phone) lives on {@link User}; this table only holds
 * worker-specific operational state (currently just the FCM token used by the mobile app).
 */
@Entity
@Table(name = "challenged_workers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChallengedWorker extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "fcm_token", length = 500)
    private String fcmToken;

    public static ChallengedWorker of(User user) {
        ChallengedWorker w = new ChallengedWorker();
        w.user = user;
        return w;
    }

    public void updateFcmToken(String token) {
        this.fcmToken = token;
    }
}
