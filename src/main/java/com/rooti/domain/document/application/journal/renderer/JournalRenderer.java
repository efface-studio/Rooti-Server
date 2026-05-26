package com.rooti.domain.document.application.journal.renderer;

import com.rooti.domain.document.application.JournalFormat;
import com.rooti.domain.document.application.journal.JournalDocument;

/**
 * 표현-무관 {@link JournalDocument} 를 특정 파일 형식의 바이트 배열로 변환하는 렌더러.
 *
 * <p>한 형식당 한 구현체. Spring 이 모든 구현을 모아 {@link JournalRendererRegistry} 가
 * format → renderer 매핑을 들고 있게 합니다. 새 형식을 추가하려면 새 구현체와 enum 값을 더하는 것
 * 만으로 충분하며, 디스패치 코드를 손댈 필요는 없습니다 (Open/Closed).
 */
public interface JournalRenderer {

    /** 이 렌더러가 처리하는 출력 형식. */
    JournalFormat format();

    /** 문서를 렌더링해 그 형식의 raw 바이트 배열을 반환합니다. */
    byte[] render(JournalDocument document);
}
