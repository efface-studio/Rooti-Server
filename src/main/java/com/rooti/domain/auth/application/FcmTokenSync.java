package com.rooti.domain.auth.application;

import com.rooti.domain.user.domain.User;
import com.rooti.domain.user.domain.UserRole;
import com.rooti.domain.worker.infrastructure.ChallengedWorkerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 로그인 시 클라이언트가 보내준 FCM 디바이스 토큰을 사용자의 역할별 테이블에 기록합니다.
 *
 * <p>현재는 {@code WORKER} 만 푸시 알림 대상이므로 해당 역할의 매핑만 갱신합니다. 추후 보호자 /
 * 담당자도 디바이스 푸시를 받게 되면 이 클래스의 분기만 늘리면 되고, 로그인 흐름 자체는 영향을
 * 받지 않습니다 (단일 책임 원칙 — "FCM 토큰을 어디에 둘지" 는 한 곳에서만 답합니다).
 */
@Component
@RequiredArgsConstructor
public class FcmTokenSync {

    private final ChallengedWorkerRepository challengedWorkerRepository;

    /**
     * @param user 방금 로그인한 사용자
     * @param fcmToken 클라이언트가 함께 보낸 디바이스 토큰. null 또는 공백이면 호출하지 마세요.
     */
    public void persist(User user, String fcmToken) {
        if (user.getRole() == UserRole.WORKER) {
            challengedWorkerRepository
                    .findByUserId(user.getId())
                    .ifPresent(w -> w.updateFcmToken(fcmToken));
        }
        // 보호자 / 담당자의 디바이스 알림은 아직 별도 푸시 매핑이 없어 no-op 입니다.
    }
}
