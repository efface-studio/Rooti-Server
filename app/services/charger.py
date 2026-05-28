"""Company charger service — 회사 담당자 목록/수정."""

from __future__ import annotations

from sqlalchemy import func, or_, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.exceptions import BusinessException, ErrorCode
from app.core.pagination import Page, PageParams
from app.models import Company, CompanyCharger, User
from app.schemas.charger import ChargerResponse


class ChargerService:
    def __init__(self, db: AsyncSession) -> None:
        self.db = db

    async def search(
        self, keyword: str | None, company_id: int | None, params: PageParams
    ) -> Page[ChargerResponse]:
        # CompanyCharger ⋈ User ⋈ Company — 한 번에 표시 정보까지.
        base = (
            select(CompanyCharger, User, Company)
            .join(User, User.id == CompanyCharger.user_id)
            .join(Company, Company.id == CompanyCharger.company_id)
        )
        if company_id is not None:
            base = base.where(CompanyCharger.company_id == company_id)
        if keyword:
            like = f"%{keyword.strip()}%"
            base = base.where(
                or_(User.name.ilike(like), User.username.ilike(like), Company.name.ilike(like))
            )
        total = int(
            (await self.db.execute(select(func.count()).select_from(base.subquery()))).scalar_one()
            or 0
        )
        rows = (
            await self.db.execute(
                base.order_by(CompanyCharger.id).offset(params.offset).limit(params.limit)
            )
        ).all()
        content = [_to_response(cc, u, c) for cc, u, c in rows]
        return Page.build(content, params=params, total_elements=total)

    async def set_hired(self, charger_id: int, hired: bool) -> ChargerResponse:
        cc = await self.db.get(CompanyCharger, charger_id)
        if cc is None:
            raise BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "담당자를 찾을 수 없습니다.")
        cc.is_hired = hired
        user = await self.db.get(User, cc.user_id)
        company = await self.db.get(Company, cc.company_id)
        assert user is not None and company is not None
        return _to_response(cc, user, company)


def _to_response(cc: CompanyCharger, user: User, company: Company) -> ChargerResponse:
    return ChargerResponse(
        id=cc.id,
        user_id=user.id,
        username=user.username,
        name=user.name,
        phone_number=user.phone_number,
        email=user.email,
        company_id=company.id,
        company_name=company.name,
        hired=cc.is_hired,
    )
