package com.rooti.domain.company.domain;

import com.rooti.domain.user.domain.User;
import com.rooti.global.audit.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Company-side staff member. 1:1 with a {@link User} of role {@code CHARGER}. */
@Entity
@Table(name = "company_chargers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanyCharger extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "is_hired", nullable = false)
    private boolean hired;

    @Column(name = "fcm_token", length = 500)
    private String fcmToken;

    @Builder
    private CompanyCharger(User user, Company company, Boolean hired, String fcmToken) {
        this.user = user;
        this.company = company;
        this.hired = hired == null || hired;
        this.fcmToken = fcmToken;
    }

    public void leave() {
        this.hired = false;
    }

    public void rehire() {
        this.hired = true;
    }

    public void updateFcmToken(String token) {
        this.fcmToken = token;
    }
}
