package com.rooti.domain.document.application.journal;

import java.time.LocalDate;
import java.util.List;

/**
 * 근무일지 한 부의 표현-무관 데이터.
 *
 * <p>PDF · HWP · XLSX 렌더러는 모두 이 객체 하나만 받아서 각자의 표기로 변환합니다 — 즉,
 * "회사명 / 일자 / 작업 시간표 / 휴가 / 익일 계획" 같은 본질적인 도메인 데이터는 한 군데에서만
 * 만들어지고, 렌더러는 표현 책임만 집니다. 같은 데이터를 세 형식으로 내보낼 때 사실관계가
 * 어긋나지 않도록 보장하기 위한 핵심 경계입니다.
 */
public record JournalDocument(
        Header header,
        List<ProcessRow> rows,
        List<LeaveEntry> leaves,
        String remarks,
        List<NextPlan> nextPlans) {

    /**
     * 일지 상단 정보 블록. 모든 필드는 사람이 읽는 라벨이며 표시용 가공이 이미 끝난 상태로
     * 들어옵니다 (예: 일자는 "2026년 05월 26일 (화요일)" 처럼 한국어 풀텍스트).
     */
    public record Header(
            String companyName,
            LocalDate date,
            String dateLabelKor,
            String jobStandardName,
            String workerName,
            String workerRank) {}

    /**
     * 작업표 한 행. 시작/종료는 표시용 "HH:mm" 으로 정규화되며 duration 은 분 단위 (최소 1분 보장).
     * note 는 자유 서술이라 빈 문자열일 수 있습니다.
     */
    public record ProcessRow(String startTime, String endTime, String label, long durationMinutes, String note) {}

    /** 같은 날 같은 근로자의 승인된 휴가 한 건. 일지 상단에 별도 박스로 노출됩니다. */
    public record LeaveEntry(LeaveType type, String rangeLabel, int days, String reason) {
        public enum LeaveType {
            ANNUAL("연차"),
            MONTHLY("월차"),
            SICK("병가"),
            OTHER("기타");

            private final String label;

            LeaveType(String label) {
                this.label = label;
            }

            public String label() {
                return label;
            }
        }
    }

    /** 익일업무계획 한 행. expectedMinutes 가 null 이면 표 칸은 공란. */
    public record NextPlan(String task, Integer expectedMinutes, String instruction) {}
}
