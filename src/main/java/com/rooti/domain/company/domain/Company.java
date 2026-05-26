package com.rooti.domain.company.domain;

import com.rooti.global.audit.BaseEntity;
import com.rooti.global.util.JsonAttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A tenant in the system. Owns workers, chargers, jobs, schedules — virtually every other
 * aggregate ultimately roots back here.
 *
 * <p>{@code templateData} stores per-company UI/document templates as JSONB; the schema is
 * intentionally loose to preserve the legacy CKEditor-driven templating, but the
 * {@link JsonAttributeConverter} keeps it strongly typed at the Java boundary.
 */
@Entity
@Table(name = "companies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Company extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 500)
    private String location;

    @Column(name = "use_flag", nullable = false)
    private boolean useFlag;

    @Column(name = "image_path", length = 500)
    private String imagePath;

    @Column(name = "template_id", length = 100)
    private String templateId;

    @Convert(converter = JsonAttributeConverter.class)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "template_data", columnDefinition = "jsonb")
    private Map<String, Object> templateData;

    @Builder
    private Company(
            String name,
            String location,
            Boolean useFlag,
            String imagePath,
            String templateId,
            Map<String, Object> templateData) {
        this.name = name;
        this.location = location;
        this.useFlag = useFlag == null || useFlag;
        this.imagePath = imagePath;
        this.templateId = templateId;
        this.templateData = templateData;
    }

    public void rename(String name) {
        if (name != null && !name.isBlank()) this.name = name;
    }

    public void relocate(String location) {
        this.location = location;
    }

    public void changeImage(String imagePath) {
        this.imagePath = imagePath;
    }

    public void changeTemplate(String templateId, Map<String, Object> data) {
        this.templateId = templateId;
        this.templateData = data;
    }

    public void deactivate() {
        this.useFlag = false;
    }

    public void activate() {
        this.useFlag = true;
    }
}
