"""Kiosk service — company ↔ kiosk binding + 관리 목록."""

from __future__ import annotations

from sqlalchemy import func, or_, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.exceptions import BusinessException, ErrorCode
from app.core.pagination import Page, PageParams
from app.core.tenant import CompanyScopeValue, assert_company
from app.models import Company, CompanyKiosk
from app.schemas.kiosk import KioskBindRequest, KioskResponse


class KioskService:
    def __init__(self, db: AsyncSession) -> None:
        self.db = db

    async def list_by_company(self, company_id: int) -> list[KioskResponse]:
        rows = (
            await self.db.execute(
                select(CompanyKiosk, Company.name)
                .join(Company, Company.id == CompanyKiosk.company_id)
                .where(CompanyKiosk.company_id == company_id)
                .order_by(CompanyKiosk.id)
            )
        ).all()
        return [_to_response(k, cname) for k, cname in rows]

    async def search(
        self,
        keyword: str | None,
        company_id: int | None,
        status: str | None,
        params: PageParams,
    ) -> Page[KioskResponse]:
        base = select(CompanyKiosk, Company.name).join(
            Company, Company.id == CompanyKiosk.company_id
        )
        if company_id is not None:
            base = base.where(CompanyKiosk.company_id == company_id)
        if status:
            base = base.where(CompanyKiosk.status == status)
        if keyword:
            like = f"%{keyword.strip()}%"
            base = base.where(
                or_(
                    CompanyKiosk.name.ilike(like),
                    CompanyKiosk.kiosk_id.ilike(like),
                    Company.name.ilike(like),
                )
            )
        total = int(
            (await self.db.execute(select(func.count()).select_from(base.subquery()))).scalar_one()
            or 0
        )
        rows = (
            await self.db.execute(
                base.order_by(CompanyKiosk.id).offset(params.offset).limit(params.limit)
            )
        ).all()
        content = [_to_response(k, cname) for k, cname in rows]
        return Page.build(content, params=params, total_elements=total)

    async def bind(self, req: KioskBindRequest) -> KioskResponse:
        company = await self.db.get(Company, req.company_id)
        if company is None:
            raise BusinessException(ErrorCode.COMPANY_NOT_FOUND)

        dup = (
            await self.db.execute(
                select(CompanyKiosk.id).where(
                    CompanyKiosk.company_id == req.company_id,
                    CompanyKiosk.kiosk_id == req.kiosk_id,
                )
            )
        ).first()
        if dup:
            raise BusinessException(ErrorCode.CONFLICT, "이미 등록된 키오스크입니다.")

        kiosk = CompanyKiosk(
            company_id=req.company_id,
            kiosk_id=req.kiosk_id,
            name=req.name or req.kiosk_id,
            location=req.location,
            capacity=req.capacity,
            current_count=0,
            status="OFFLINE",
        )
        self.db.add(kiosk)
        await self.db.flush()
        return _to_response(kiosk, company.name)

    async def set_assignee(
        self, kiosk_id: int, assignee: str | None, *, company_scope: CompanyScopeValue = None
    ) -> KioskResponse:
        kiosk = await self.db.get(CompanyKiosk, kiosk_id)
        if kiosk is None:
            raise BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "키오스크를 찾을 수 없습니다.")
        assert_company(company_scope, kiosk.company_id)
        kiosk.assignee = assignee
        company = await self.db.get(Company, kiosk.company_id)
        return _to_response(kiosk, company.name if company else None)

    async def unbind(self, kiosk_id: int, *, company_scope: CompanyScopeValue = None) -> None:
        kiosk = await self.db.get(CompanyKiosk, kiosk_id)
        if kiosk is not None:
            assert_company(company_scope, kiosk.company_id)
            await self.db.delete(kiosk)


def _to_response(k: CompanyKiosk, company_name: str | None) -> KioskResponse:
    return KioskResponse(
        id=k.id,
        company_id=k.company_id,
        company_name=company_name,
        kiosk_id=k.kiosk_id,
        name=k.name,
        location=k.location,
        capacity=k.capacity,
        current=k.current_count,
        status=k.status,
        assignee=k.assignee,
        last_reported_at=k.last_reported_at,
    )
