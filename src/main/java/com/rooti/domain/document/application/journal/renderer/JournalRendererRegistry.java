package com.rooti.domain.document.application.journal.renderer;

import com.rooti.domain.document.application.JournalFormat;
import com.rooti.global.exception.BusinessException;
import com.rooti.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * format → renderer 매핑을 한 군데에 모아두는 lookup 컴포넌트.
 *
 * <p>Spring 이 같은 {@code @Component} 인터페이스의 모든 구현체를 컬렉션으로 주입해 주는 점을
 * 활용해, 새 형식이 추가돼도 별도 등록 코드 없이 그 구현체만 두면 자동으로 등록됩니다. 디스패치
 * 코드는 {@link #rendererFor(JournalFormat)} 한 메서드뿐입니다.
 */
@Component
public class JournalRendererRegistry {

    private final Map<JournalFormat, JournalRenderer> byFormat;

    public JournalRendererRegistry(List<JournalRenderer> renderers) {
        this.byFormat =
                renderers.stream()
                        .collect(Collectors.toUnmodifiableMap(JournalRenderer::format, Function.identity()));
    }

    public JournalRenderer rendererFor(JournalFormat format) {
        JournalRenderer r = byFormat.get(format);
        if (r == null) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "지원하지 않는 근무일지 형식입니다: " + format);
        }
        return r;
    }
}
