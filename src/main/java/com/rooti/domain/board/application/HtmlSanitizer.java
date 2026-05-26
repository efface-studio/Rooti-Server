package com.rooti.domain.board.application;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * Single point of XSS containment for rich-text bodies before they hit the DB.
 *
 * <p>Allowlist tuned for legacy CKEditor output: paragraphs, headings, links, lists, basic
 * formatting, images with http(s)/data src, tables.
 */
@Component
public class HtmlSanitizer {

    private final Safelist safelist;

    public HtmlSanitizer() {
        this.safelist =
                Safelist.relaxed()
                        .addAttributes(":all", "style", "class")
                        .addProtocols("img", "src", "http", "https", "data")
                        .addProtocols("a", "href", "http", "https", "mailto");
    }

    public String sanitize(String html) {
        if (html == null) return null;
        return Jsoup.clean(html, "", safelist, new org.jsoup.nodes.Document.OutputSettings().prettyPrint(false));
    }
}
