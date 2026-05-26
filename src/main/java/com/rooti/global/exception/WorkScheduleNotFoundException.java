package com.rooti.global.exception;

/** 근무 스케줄을 ID 로 찾을 수 없을 때, 또는 조회 조건(회사+날짜) 으로 매칭이 0건일 때. */
public class WorkScheduleNotFoundException extends BusinessException {

    public WorkScheduleNotFoundException(long scheduleId) {
        super(ErrorCode.WORK_SCHEDULE_NOT_FOUND, "스케줄을 찾을 수 없습니다 (id=" + scheduleId + ")");
    }

    public WorkScheduleNotFoundException(String message) {
        super(ErrorCode.WORK_SCHEDULE_NOT_FOUND, message);
    }
}
