package com.rooti.domain.document.application.journal.renderer;

import com.rooti.domain.document.application.JournalFormat;
import com.rooti.domain.document.application.journal.JournalDocument;
import com.rooti.domain.document.application.journal.JournalDocument.Header;
import com.rooti.domain.document.application.journal.JournalDocument.LeaveEntry;
import com.rooti.domain.document.application.journal.JournalDocument.NextPlan;
import com.rooti.domain.document.application.journal.JournalDocument.ProcessRow;
import com.rooti.global.exception.BusinessException;
import com.rooti.global.exception.ErrorCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
import org.springframework.stereotype.Component;

/**
 * XLSX 렌더러 — Apache POI 로 동일 레이아웃을 그립니다.
 *
 * <p>워크북은 정산/집계 매크로가 그대로 흡수할 수 있도록 정보 표 / 작업 표 / 휴가 표 / 특이사항 /
 * 익일업무계획을 한 시트에 순서대로 쌓고, 헤더 셀에는 색 채움을 줘서 사람이 봐도 구획이 잡힙니다.
 */
@Component
public class XlsxJournalRenderer implements JournalRenderer {

    private static final int COL_COUNT = 4;

    @Override
    public JournalFormat format() {
        return JournalFormat.XLSX;
    }

    @Override
    public byte[] render(JournalDocument doc) {
        try (Workbook wb = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet("근무일지");
            Styles s = new Styles(wb);

            // 컬럼 폭 (단위: 1/256 char)
            int[] widths = {18 * 256, 32 * 256, 14 * 256, 30 * 256};
            for (int i = 0; i < widths.length; i++) sh.setColumnWidth(i, widths[i]);

            int row = 0;
            row = writeTitle(sh, row, s, doc.header());
            row = writeInfoTable(sh, row, s, doc.header());
            row = writeLeavesTable(sh, row, s, doc.leaves());
            row = writeProcessTable(sh, row, s, doc.rows());
            row = writeRemarks(sh, row, s, doc.remarks());
            writeNextPlans(sh, row, s, doc.nextPlans());

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "XLSX 생성에 실패했습니다.", e);
        }
    }

    // ----- section writers --------------------------------------------------
    private int writeTitle(Sheet sh, int row, Styles s, Header h) {
        Row titleRow = sh.createRow(row);
        titleRow.setHeightInPoints(28f);
        put(titleRow, 0, h.companyName() + " 근무일지", s.title);
        sh.addMergedRegion(new CellRangeAddress(row, row, 0, COL_COUNT - 1));
        return row + 2;
    }

    private int writeInfoTable(Sheet sh, int row, Styles s, Header h) {
        Row r1 = sh.createRow(row++);
        put(r1, 0, "일자", s.label);
        put(r1, 1, h.dateLabelKor(), s.bodyCenter);
        put(r1, 2, "업무명", s.label);
        put(r1, 3, h.jobStandardName(), s.bodyCenter);

        Row r2 = sh.createRow(row++);
        put(r2, 0, "직급", s.label);
        put(r2, 1, h.workerRank(), s.bodyCenter);
        put(r2, 2, "성명", s.label);
        put(r2, 3, h.workerName(), s.bodyCenter);
        return row + 1;
    }

    private int writeLeavesTable(Sheet sh, int row, Styles s, java.util.List<LeaveEntry> leaves) {
        if (leaves.isEmpty()) return row;
        Row head = sh.createRow(row);
        put(head, 0, "휴가 (이 날 근무가 휴가로 처리되었습니다)", s.headWarn);
        sh.addMergedRegion(new CellRangeAddress(row, row, 0, COL_COUNT - 1));
        row++;
        Row colHead = sh.createRow(row++);
        put(colHead, 0, "구분", s.head);
        put(colHead, 1, "기간", s.head);
        put(colHead, 2, "일수", s.head);
        put(colHead, 3, "사유", s.head);
        for (LeaveEntry l : leaves) {
            Row r = sh.createRow(row++);
            put(r, 0, l.type().label(), s.bodyCenter);
            put(r, 1, l.rangeLabel(), s.bodyCenter);
            put(r, 2, l.days() + "일", s.bodyCenter);
            put(r, 3, nz(l.reason()), s.body);
        }
        return row + 1;
    }

    private int writeProcessTable(Sheet sh, int row, Styles s, java.util.List<ProcessRow> rows) {
        Row head = sh.createRow(row++);
        put(head, 0, "업무시간", s.head);
        put(head, 1, "업무내용", s.head);
        put(head, 2, "시간", s.head);
        put(head, 3, "비고", s.head);

        if (rows.isEmpty()) {
            Row r = sh.createRow(row);
            put(r, 0, "기록된 작업이 없습니다.", s.bodyCenter);
            sh.addMergedRegion(new CellRangeAddress(row, row, 0, COL_COUNT - 1));
            return row + 2;
        }
        for (ProcessRow p : rows) {
            Row r = sh.createRow(row++);
            put(r, 0, p.startTime() + " - " + p.endTime(), s.bodyCenter);
            put(r, 1, p.label(), s.bodyCenter);
            put(r, 2, p.durationMinutes() + "분", s.bodyCenter);
            put(r, 3, nz(p.note()), s.body);
        }
        return row + 1;
    }

    private int writeRemarks(Sheet sh, int row, Styles s, String remarks) {
        Row head = sh.createRow(row);
        put(head, 0, "특이사항", s.head);
        sh.addMergedRegion(new CellRangeAddress(row, row, 0, COL_COUNT - 1));
        row++;
        Row body = sh.createRow(row);
        body.setHeightInPoints(48f);
        put(body, 0, nz(remarks), s.body);
        sh.addMergedRegion(new CellRangeAddress(row, row, 0, COL_COUNT - 1));
        return row + 2;
    }

    private void writeNextPlans(Sheet sh, int row, Styles s, java.util.List<NextPlan> plans) {
        Row head = sh.createRow(row);
        put(head, 0, "익일업무계획", s.head);
        put(head, 1, "예상시간", s.head);
        put(head, 2, "지시사항", s.head);
        sh.addMergedRegion(new CellRangeAddress(row, row, 2, 3));
        row++;
        for (NextPlan p : plans) {
            Row r = sh.createRow(row++);
            put(r, 0, nz(p.task()), s.bodyCenter);
            put(r, 1, p.expectedMinutes() == null ? "" : p.expectedMinutes() + "분", s.bodyCenter);
            put(r, 2, nz(p.instruction()), s.body);
            sh.addMergedRegion(new CellRangeAddress(r.getRowNum(), r.getRowNum(), 2, 3));
        }
    }

    // ----- helpers ----------------------------------------------------------
    private static Cell put(Row row, int col, String v, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(v);
        c.setCellStyle(style);
        return c;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** 워크북에 사용되는 셀 스타일을 한 곳에서 만들어 재사용합니다. */
    private static final class Styles {
        final CellStyle title;
        final CellStyle label;
        final CellStyle head;
        final CellStyle headWarn;
        final CellStyle body;
        final CellStyle bodyCenter;

        Styles(Workbook wb) {
            this.title = boldCenter(wb, (short) 16);
            this.body = bordered(wb, HorizontalAlignment.LEFT);
            this.bodyCenter = bordered(wb, HorizontalAlignment.CENTER);
            this.label = filledHeader(wb, IndexedColors.GREY_25_PERCENT);
            this.head = filledHeader(wb, IndexedColors.PALE_BLUE);
            this.headWarn = filledHeader(wb, IndexedColors.LIGHT_YELLOW);
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

        private static CellStyle filledHeader(Workbook wb, IndexedColors color) {
            CellStyle s = bordered(wb, HorizontalAlignment.CENTER);
            s.setFillForegroundColor(color.getIndex());
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
}
