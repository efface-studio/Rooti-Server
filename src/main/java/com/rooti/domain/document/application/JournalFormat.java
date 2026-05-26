package com.rooti.domain.document.application;

/**
 * Output format of a single work-journal entry inside a bulk-email ZIP.
 *
 * <p>{@link #PDF} is the canonical printable format and is what the existing paper form ("청주
 * 바른기준치과 근무일지" 류) translates into directly — rendered by openhtmltopdf from an inline
 * Korean-fonted HTML template. {@link #XLSX} writes the same fields into a spreadsheet for
 * accounting / aggregation pipelines via Apache POI. {@link #HWP} targets 관공서 제출 — rendered
 * through a hwpx template; until the hwpx renderer ships the controller still accepts the format
 * but the renderer falls back to a labelled placeholder so the email pipeline does not stall.
 */
public enum JournalFormat {
    PDF("pdf", "application/pdf"),
    HWP("hwp", "application/x-hwp"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final String extension;
    private final String contentType;

    JournalFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }
}
