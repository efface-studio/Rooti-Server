# Rooti v2 — 배포 & 비용 가이드

v2(FastAPI) 운영 배포와, 구 Django "메인서버" 대비 비용 최적화 방법을 정리한다.

> ⚠️ 아래 비용은 **ap-northeast-2 온디맨드 정가** 기준 추정이다. 계정의 실제
> 청구액·예약(RI)/Savings Plan 적용 여부는 IAM 권한 제약으로 확인하지 못했다
> (Cost Explorer / Pricing API / ElastiCache 조회 거부). 약정 할인이 있으면
> 실제 비용은 더 낮다.

## 1. 현재 메인서버(구 Django) 인프라 (요약)

| 리소스 | 사양 |
| --- | --- |
| EC2 (app) | t2.micro |
| RDS | db.t3.medium · gp3 200GB · **Multi-AZ** · 백업 7일 |
| Redis | 별도 ElastiCache 없이 EC2 내부로 추정 |

실데이터는 수 MB 수준인데 200GB 가 할당돼 있다(과대 할당).

## 2. 월 비용 추정 (정가)

| 항목 | 사양 | $/월 |
| --- | --- | --- |
| RDS 인스턴스 | db.t3.medium ×2 (Multi-AZ) | 151.8 |
| RDS 스토리지 | gp3 200GB ×2 | 52.4 |
| EC2 | t2.micro | 10.5 |
| EBS + IPv4 | 8GB gp2 + EIP 1 | 4.6 |
| **합계(메인서버)** | | **≈ $219/월 (₩약 30만)** |

요금의 **~93% 가 RDS**. 이 비용은 "데이터·가용성(Multi-AZ)" 때문이지 프레임워크
(Django/FastAPI) 때문이 아니다.

## 3. v2 비용 시나리오

v2 는 같은 앱·같은 데이터·같은 DB 를 쓰므로, **프레임워크 교체만으로는 요금이
줄지 않는다.** v2 의 효율(async · 커넥션풀 5~10 · Redis 캐시 · 페이지네이션)이
**다운사이징을 가능하게** 하는 것이 실제 절감 레버다.

| 시나리오 | 구성 | $/월 | 메인서버 대비 |
| --- | --- | --- | --- |
| A. 같은 사양 교체 | medium Multi-AZ 유지, EC2 t4g.micro | ~$216 | ≈ $0 |
| **B. 효율 기반 다운사이징(권장)** | **db.t3.small** Multi-AZ + 스토리지 50GB(오토스케일) + t4g.micro | **~$101** | **−$118 (~54%↓)** |
| C. 공격적(비권장) | small **single-AZ** 50GB | ~$57 | −$162, 가용성 포기 |

권장: **시나리오 B**. 새 인스턴스를 처음부터 작게 띄우면 RDS 스토리지 축소 제약도 회피된다.

## 4. 프로덕션 컷오버 런북

```
0. 사전: 메인서버 RDS 스냅샷 (롤백 대비).
1. v2용 RDS 프로비저닝 (권장 사양):
   - PostgreSQL 16 · db.t3.small · Multi-AZ · gp3 50GB · 스토리지 오토스케일(상한 200GB)
   - TLS 강제, 보안그룹은 앱 서버/배스천 IP 만 허용 (퍼블릭 액세스 OFF 권장)
   - 전용 앱 유저(rooti_app, 최소권한) 생성
2. 스키마 적용:  migrations/V1__…  ~  V6__  (legacy-migration/README.md 참고)
3. 데이터 이관:  legacy-migration/ 의 Django→v2 스크립트 실행 + V2-verify.sql 로 카운트 검증
4. 앱 배포:  .env.prod (← .env.prod.example) 채우고
             gunicorn app.main:app -k uvicorn.workers.UvicornWorker
5. 스모크: /actuator/health, 로그인, 핵심 조회 확인 후 트래픽 전환(DNS/LB)
6. 안정화 후:  legacy-migration/V3-drop-django-tables.sql 로 Django 테이블 정리
```

> dev 검증 완료: dev RDS 에 별도 DB `rooti_v2` 생성 → V1~V6 적용 → 데모 시드 →
> v2 가 SSL 로 정상 동작(로그인/조회) 확인. prod 는 위 4~6 으로 실데이터 이관이 다르다.

## 5. 프레임워크 무관 즉시 절감 (지금도 가능)

- **스토리지 과대할당**: 실데이터 ~MB 인데 200GB 할당. v2 신규 인스턴스를 50GB+오토스케일로.
- **유휴 EIP 정리**: 인스턴스 미연결 Elastic IP 점검·해제 (개당 ~$3.6/월).
- **RI / Savings Plan**: EC2·RDS 1년 약정 시 추가 ~30–40% 절감.
- **RDS 퍼블릭 액세스 점검**: 퍼블릭이면 보안그룹을 앱/배스천 IP 로 축소하거나 프라이빗 전환 권장(비용 외 보안).
