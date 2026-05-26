package com.rooti.domain.document.application.journal;

import com.rooti.domain.document.application.JournalFormat;
import com.rooti.domain.schedule.domain.WorkSchedule;
import com.rooti.global.exception.BusinessException;
import com.rooti.global.exception.ErrorCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 여러 스케줄의 일지를 하나의 ZIP 으로 묶는 책임만 가집니다.
 *
 * <p>각 엔트리는 회사가 종이로 받아오던 컨벤션 그대로
 * {@code yyyy-MM-dd~yyyy-MM-dd_{회사}_{근로자}_근무일지.{ext}} 형식의 이름을 갖습니다. 일지를
 * 어떤 형식으로 렌더링할지는 {@link WorkJournalRenderService} 가 결정하고, 이 클래스는 그 결과
 * 바이트를 받아 ZIP 으로 압축만 합니다 — 책임이 깔끔하게 분리되어, ZIP 명명 규칙을 바꿀 때
 * 렌더러 코드는 건드리지 않아도 됩니다.
 */
@Component
@RequiredArgsConstructor
public class JournalZipPackager {

    private final WorkJournalRenderService renderService;

    /**
     * 주어진 스케줄들의 일지를 {@code format} 형식으로 렌더링해 하나의 ZIP 바이트로 묶어 돌려줍니다.
     *
     * @param schedules ZIP 안에 포함될 스케줄들
     * @param date 일자 (파일명에 쓰임)
     * @param format PDF / HWP / XLSX
     * @param companyName 회사명 (파일명에 쓰임; 공백은 제거)
     */
    public byte[] pack(
            List<WorkSchedule> schedules, LocalDate date, JournalFormat format, String companyName) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            String company = sanitize(companyName);
            for (WorkSchedule s : schedules) {
                byte[] body = renderService.render(s.getId(), format);
                String worker =
                        sanitize(
                                s.getJobWorker().getCompanyWorker().getWorker().getUser().getName());
                String entryName =
                        String.format(
                                "%s~%s_%s_%s_근무일지.%s",
                                date, date, company, worker, format.extension());
                zip.putNextEntry(new ZipEntry(entryName));
                zip.write(body);
                zip.closeEntry();
            }
            zip.finish();
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "ZIP 묶음 생성에 실패했습니다.", e);
        }
    }

    private static String sanitize(String name) {
        return name == null ? "" : name.replaceAll("\\s+", "");
    }
}
