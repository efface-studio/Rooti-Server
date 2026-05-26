package com.rooti.domain.kiosk.domain;

import com.rooti.domain.company.domain.Company;
import com.rooti.global.audit.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Hivits-Pet kiosk binding for a company. (kiosk_id is the device's serial / friendly id.) */
@Entity
@Table(
        name = "company_kiosks",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_company_kiosk",
                        columnNames = {"company_id", "kiosk_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanyKiosk extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "kiosk_id", nullable = false, length = 100)
    private String kioskId;

    public static CompanyKiosk bind(Company company, String kioskId) {
        CompanyKiosk k = new CompanyKiosk();
        k.company = company;
        k.kioskId = kioskId;
        return k;
    }
}
