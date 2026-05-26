package com.rooti.domain.document.application;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.rooti.global.exception.BusinessException;
import com.rooti.global.exception.ErrorCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.springframework.stereotype.Component;

/**
 * HTML → PDF renderer.
 *
 * <p>Drop-in replacement for the legacy WeasyPrint Python dependency. The input is full HTML +
 * inlined CSS — caller is responsible for choosing fonts that can render Korean (NanumGothic
 * is bundled in {@code /static/fonts/}).
 *
 * <p>Use a typical "letterpaper" CSS sheet:
 * <pre>{@code
 * @page { size: A4; margin: 18mm; }
 * body { font-family: 'NanumGothic', sans-serif; font-size: 11pt; }
 * }</pre>
 */
@Slf4j
@Component
public class PdfGenerator {

    public byte[] renderHtmlToPdf(String html) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            org.w3c.dom.Document doc =
                    new W3CDom().fromJsoup(Jsoup.parse(html, "UTF-8"));
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withW3cDocument(doc, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            log.error("PDF render failed", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "PDF 생성에 실패했습니다.", e);
        }
    }
}
