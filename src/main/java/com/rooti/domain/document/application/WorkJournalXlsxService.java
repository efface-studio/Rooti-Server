package com.rooti.domain.document.application;

import com.rooti.domain.schedule.application.WorkScheduleService;
import com.rooti.domain.schedule.domain.WorkSchedule;
import com.rooti.domain.workrecord.application.WorkRecordService;
import com.rooti.domain.workrecord.presentation.dto.WorkRecordDtos.ProcessResponse;
import com.rooti.global.exception.BusinessException;
import com.rooti.global.exception.ErrorCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Renders a single work-journal as an .xlsx workbook mirroring the PDF layout 1:1.
 *
 * <p>Same data, same section order, same column meanings — the only thing that differs is the
 * canvas. Account / payroll teams typically receive this format because their downstream tools
 * (회계 프로그램, 정산 매크로 등) consume spreadsheets directly without re-keying.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkJournalXlsxService {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private final WorkScheduleService scheduleService;
    private final WorkRecordService recordService;

    public byte[] renderForSchedule(long scheduleId) {
        WorkSchedule s = scheduleService.getOrThrow(scheduleId);
        List<ProcessResponse> processes = recordService.listProcess(scheduleId);

        try (Workbook wb = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet("근무일지");

            CellStyle title = boldCenter(wb, (short) 16);
            CellStyle headLabel = headerLabel(wb);
            CellStyle headBlue = headerBlue(wb);
            CellStyle body = bordered(wb, HorizontalAlignment.LEFT);
            CellStyle bodyCenter = bordered(wb, HorizontalAlignment.CENTER);

            String companyName = s.getJobStandard().getCompany().getName();
            String jobName = s.getJobStandard().getName();
            String workerName = s.getJobWorker().getCompanyWorker().getWorker().getUser().getName();
            String dateKor =
                    s.getStartAt() == null
                            ? ""
                            : String.format(
                                    "%d년 %02d월 %02d일 (%s)",
                                    s.getStartAt().getYear(),
                                    s.getStartAt().getMonthValue(),
                                    s.getStartAt().getDayOfMonth(),
                                    s.getStartAt()
                                            .getDayOfWeek()
                                            .getDisplayName(TextStyle.FULL, Locale.KOREAN));

            // Column widths (단위: 1/256 char) — info / process 두 표를 한 시트에 얹기 위해 균등 폭
            int[] widths = {18 * 256, 32 * 256, 14 * 256, 30 * 256};
            for (int i = 0; i < widths.length; i++) sh.setColumnWidth(i, widths[i]);

            int r = 0;

            // Title row spanning all columns
            Row titleRow = sh.createRow(r);
            titleRow.setHeightInPoints(28f);
            put(titleRow, 0, companyName + " 근무일지", title);
            sh.addMergedRegion(new CellRangeAddress(r, r, 0, 3));
            r += 2;

            // Info table 2 rows
            Row info1 = sh.createRow(r);
            put(info1, 0, "일자", headLabel);
            put(info1, 1, dateKor, bodyCenter);
            put(info1, 2, "업무명", headLabel);
            put(info1, 3, jobName, bodyCenter);
            r++;
            Row info2 = sh.createRow(r);
            put(info2, 0, "직급", headLabel);
            put(info2, 1, "사무보조사원", bodyCenter);
            put(info2, 2, "성명", headLabel);
            put(info2, 3, workerName, bodyCenter);
            r += 2;

            // Process header
            Row procHead = sh.createRow(r);
            put(procHead, 0, "업무시간", headBlue);
            put(procHead, 1, "업무내용", headBlue);
            put(procHead, 2, "시간", headBlue);
            put(procHead, 3, "비고", headBlue);
            r++;

            // Process rows
            if (processes.isEmpty()) {
                Row empty = sh.createRow(r);
                put(empty, 0, "기록된 작업이 없습니다.", bodyCenter);
                sh.addMergedRegion(new CellRangeAddress(r, r, 0, 3));
                r++;
            } else {
                for (ProcessResponse p : processes) {
                    if (p.startAt() == null || p.endAt() == null) continue;
                    long minutes =
                            Math.max(1, Duration.between(p.startAt(), p.endAt()).toMinutes());
                    Row pr = sh.createRow(r++);
                    put(pr, 0, HM.format(p.startAt()) + " - " + HM.format(p.endAt()), bodyCenter);
                    put(pr, 1, safe(p.jobProcessName()), bodyCenter);
                    put(pr, 2, minutes + "분", bodyCenter);
                    put(pr, 3, safe(p.endAnswer()), body);
                }
            }
            r += 1;

            // 특이사항
            Row remarkHead = sh.createRow(r);
            put(remarkHead, 0, "특이사항", headBlue);
            sh.addMergedRegion(new CellRangeAddress(r, r, 0, 3));
            r++;
            Row remarkBody = sh.createRow(r);
            remarkBody.setHeightInPoints(48f);
            put(remarkBody, 0, "", body);
            sh.addMergedRegion(new CellRangeAddress(r, r, 0, 3));
            r += 2;

            // 익일업무계획
            Row planHead = sh.createRow(r);
            put(planHead, 0, "익일업무계획", headBlue);
            put(planHead, 1, "예상시간", headBlue);
            put(planHead, 2, "지시사항", headBlue);
            sh.addMergedRegion(new CellRangeAddress(r, r, 2, 3));
            r++;
            Row planBody = sh.createRow(r);
            put(planBody, 0, jobName, bodyCenter);
            put(planBody, 1, "", bodyCenter);
            put(planBody, 2, "", body);
            sh.addMergedRegion(new CellRangeAddress(r, r, 2, 3));

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "XLSX 생성에 실패했습니다.", e);
        }
    }

    // ----- helpers ----------------------------------------------------------
    private static Cell put(Row row, int col, String v, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(v);
        c.setCellStyle(style);
        return c;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static CellStyle boldCenter(Workbook wb, short size) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints(size);
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        return s;
    }

    private static CellStyle headerLabel(Workbook wb) {
        CellStyle s = bordered(wb, HorizontalAlignment.CENTER);
        s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        return s;
    }

    private static CellStyle headerBlue(Workbook wb) {
        CellStyle s = bordered(wb, HorizontalAlignment.CENTER);
        s.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        return s;
    }

    private static CellStyle bordered(Workbook wb, HorizontalAlignment align) {
        CellStyle s = wb.createCellStyle();
        s.setAlignment(align);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        s.setWrapText(true);
        return s;
    }
}
