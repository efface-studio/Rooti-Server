"""멀티테넌트(회사) 스코프 가드 — cross-tenant IDOR 차단.

스코프 의미:
- ``None``  → ADMIN. 모든 회사 접근 가능(감독자).
- ``int``   → CHARGER. 본인이 담당하는 단일 ``company_id`` 로만 제한.

list 류는 :func:`resolve_company_filter` 로 요청 필터와 스코프를 합성하고,
단건(by-id) 류는 :func:`assert_company` 로 리소스의 회사가 스코프와 일치하는지 검증한다.
권한 위반 메시지는 정보 누출 방지를 위해 일반화한다(어떤 회사인지 노출하지 않음).
"""

from __future__ import annotations

from app.core.exceptions import AuthForbiddenException

# scope 값: None = 전체(ADMIN), int = 해당 company_id 로 제한(CHARGER)
CompanyScopeValue = int | None


def resolve_company_filter(scope: CompanyScopeValue, requested: int | None) -> int | None:
    """list 엔드포인트용 — 스코프와 요청 company_id 필터를 합성한다.

    - ADMIN(``scope is None``): 요청값을 그대로 사용(``None`` 이면 전체 조회).
    - CHARGER(``scope`` 지정): 요청이 없으면 자기 회사로 강제,
      다른 회사를 요청하면 403.
    """
    if scope is None:
        return requested
    if requested is not None and requested != scope:
        raise AuthForbiddenException()
    return scope


def assert_company(scope: CompanyScopeValue, company_id: int | None) -> None:
    """단건(by-id) 접근용 — 리소스의 ``company_id`` 가 스코프 안에 있는지 검증.

    - ADMIN(``scope is None``): 항상 통과.
    - CHARGER: 리소스 회사가 본인 회사와 다르면(또는 회사가 없으면) 403.
    """
    if scope is not None and company_id != scope:
        raise AuthForbiddenException()
