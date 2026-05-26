package com.rooti.domain.board.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HtmlSanitizerTest {

    private final HtmlSanitizer sanitizer = new HtmlSanitizer();

    @Test
    void preserves_safe_inline_formatting() {
        String html = "<p><strong>중요</strong>한 공지</p>";
        assertThat(sanitizer.sanitize(html)).contains("<strong>중요</strong>");
    }

    @Test
    void strips_inline_script_tags() {
        String html = "<p>safe</p><script>alert('xss')</script>";
        String out = sanitizer.sanitize(html);
        assertThat(out).doesNotContainIgnoringCase("script");
        assertThat(out).contains("<p>safe</p>");
    }

    @Test
    void strips_event_handler_attributes() {
        String html = "<a href='https://rooti.io' onclick='steal()'>click</a>";
        String out = sanitizer.sanitize(html);
        assertThat(out).doesNotContain("onclick");
        assertThat(out).contains("href=\"https://rooti.io\"");
    }

    @Test
    void allows_data_uri_for_images() {
        String html = "<img src='data:image/png;base64,AAA' alt='ok'/>";
        assertThat(sanitizer.sanitize(html)).contains("src=\"data:image/png;base64,AAA\"");
    }

    @Test
    void rewrites_javascript_protocol_links() {
        String html = "<a href='javascript:steal()'>x</a>";
        String out = sanitizer.sanitize(html);
        assertThat(out).doesNotContain("javascript:");
    }

    @Test
    void null_input_round_trips_safely() {
        assertThat(sanitizer.sanitize(null)).isNull();
    }
}
