package com.rooti.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 한 HTTP 요청의 모든 로그를 같은 ID 로 묶기 위해 MDC 에 traceId 를 심고, 응답 헤더로도 흘려주는
 * 필터.
 *
 * <p>운영 환경에서 "한 사용자가 한 요청 안에서 한 일" 을 추적할 때, 모든 application/SQL/외부 호출
 * 로그가 같은 traceId 를 공유하면 디버깅 시간이 크게 줄어듭니다. 라인 · 쿠팡잇츠 · 네이버 클라우드
 * 등 거의 모든 큰 회사 기술 블로그가 동일한 패턴을 권장합니다.
 *
 * <p>흐름:
 *
 * <ol>
 *   <li>요청 진입 시 {@code X-Trace-Id} 헤더를 먼저 보고, 있으면 그대로 사용 (게이트웨이/프록시가
 *       이미 부여했을 수 있음)
 *   <li>없으면 새 UUID 를 만들어 MDC + 응답 헤더에 부착
 *   <li>요청 종료 후 MDC 를 정리해 다음 스레드에 누수되지 않게 함
 * </ol>
 *
 * <p>{@code @Order(HIGHEST_PRECEDENCE)} 로 모든 다른 필터·로깅 인터셉터보다 먼저 동작합니다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceIdFilter extends OncePerRequestFilter {

    /** Logback %X{traceId} 와 매칭되는 MDC 키. 변경 시 logback 패턴도 같이 바꿔야 합니다. */
    public static final String MDC_KEY = "traceId";

    /** 외부 프록시/게이트웨이가 추가/조회하는 표준 헤더명. */
    public static final String HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = resolveOrCreate(request);
        MDC.put(MDC_KEY, traceId);
        response.setHeader(HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private String resolveOrCreate(HttpServletRequest request) {
        String fromHeader = request.getHeader(HEADER);
        if (fromHeader != null && !fromHeader.isBlank()) return fromHeader.trim();
        // UUID 의 하이픈을 제거해 로그 폭 절약 (32→26 폭이 좁아지면서 가독성 ↑)
        return UUID.randomUUID().toString().replace("-", "");
    }
}
