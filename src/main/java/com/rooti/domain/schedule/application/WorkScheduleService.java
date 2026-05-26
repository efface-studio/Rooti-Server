package com.rooti.domain.schedule.application;

import com.rooti.domain.schedule.domain.WorkSchedule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 도메인 내부에서 스케줄 entity 만 빠르게 잡고 싶을 때 쓰는 얇은 facade.
 *
 * <p>새 코드는 가급적 {@link WorkScheduleReader} / {@link WorkScheduleWriter} 를 직접 주입받으세요
 * — 의도(읽기/쓰기) 를 호출 측에서 명확하게 드러내는 편이 협업에 좋습니다. 이 facade 는 어셈블러
 * 등 기존 호출부의 호환을 위해 남겨두며, {@code getOrThrow} 한 메서드만 노출합니다.
 */
@Service
@RequiredArgsConstructor
public class WorkScheduleService {

    private final WorkScheduleReader reader;

    public WorkSchedule getOrThrow(long id) {
        return reader.getOrThrow(id);
    }
}
