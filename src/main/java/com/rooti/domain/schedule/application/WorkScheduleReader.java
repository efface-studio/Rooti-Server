package com.rooti.domain.schedule.application;

import com.rooti.domain.schedule.domain.WorkSchedule;
import com.rooti.domain.schedule.infrastructure.WorkScheduleRepository;
import com.rooti.domain.schedule.presentation.dto.Response;
import com.rooti.global.exception.WorkScheduleNotFoundException;
import com.rooti.global.response.PageResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 근무 스케줄 도메인의 <strong>읽기 전용</strong> 진입점 (Toss/Kakao-Pay 스타일 Reader 분리).
 *
 * <p>모든 메서드가 {@code readOnly=true} 트랜잭션에서 동작하므로, dirty-checking 비용도 없고
 * 의도가 명확합니다. 다른 도메인(예: {@code JournalDocumentAssembler}) 이 스케줄을 읽을 때는
 * 반드시 이 Reader 를 통하도록 해서 "조회 경로" 와 "변경 경로" 가 코드 상에서 자연스럽게
 * 갈라지도록 합니다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkScheduleReader {

    private final WorkScheduleRepository scheduleRepository;

    /** ID 로 도메인 객체 자체를 가져옵니다. 다른 도메인 서비스가 합성 작업할 때 씁니다. */
    public WorkSchedule getOrThrow(long id) {
        return scheduleRepository
                .findById(id)
                .orElseThrow(() -> new WorkScheduleNotFoundException(id));
    }

    /** ID 로 응답 DTO 를 만들어 돌려줍니다. 컨트롤러가 직접 호출하는 경로. */
    public Response get(long id) {
        return Response.from(getOrThrow(id));
    }

    /** 한 근로자가 기간 안에 가진 모든 스케줄. 캘린더 조회용. */
    public List<Response> listForJobWorker(long jobWorkerId, LocalDate from, LocalDate to) {
        LocalDateTime f = from.atStartOfDay();
        LocalDateTime t = to.plusDays(1).atStartOfDay();
        return scheduleRepository.findInRange(jobWorkerId, f, t).stream().map(Response::from).toList();
    }

    /** 업무 표준을 기준으로 기간 + 페이지 단위로 스케줄을 조회. 운영 화면의 목록 탭. */
    public PageResponse<Response> listForStandard(
            long jobStandardId, LocalDate from, LocalDate to, Pageable pageable) {
        return PageResponse.of(
                scheduleRepository
                        .findByStandardInRange(
                                jobStandardId,
                                from.atStartOfDay(),
                                to.plusDays(1).atStartOfDay(),
                                pageable)
                        .map(Response::from));
    }
}
