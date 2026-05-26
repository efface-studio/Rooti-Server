package com.rooti.domain.document.application.journal;

import com.rooti.domain.document.application.JournalFormat;
import com.rooti.domain.document.application.journal.renderer.JournalRendererRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "스케줄 ID + 형식" → 그 형식의 파일 바이트 배열.
 *
 * <p>호출 측(컨트롤러 / 일괄 메일 서비스)이 알아야 하는 유일한 진입점입니다. 데이터 어셈블 + 형식
 * 디스패치는 내부 컴포넌트에 위임합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkJournalRenderService {

    private final JournalDocumentAssembler assembler;
    private final JournalRendererRegistry rendererRegistry;

    public byte[] render(long scheduleId, JournalFormat format) {
        JournalDocument document = assembler.assemble(scheduleId);
        return rendererRegistry.rendererFor(format).render(document);
    }
}
