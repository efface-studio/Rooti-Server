"""Kiosk service — company ↔ kiosk binding."""

from __future__ import annotations

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.exceptions import BusinessException, ErrorCode
from app.models import Company, CompanyKiosk
from app.schemas.kiosk import KioskBindRequest, KioskResponse


class KioskService:
    def __init__(self, db: AsyncSession) -> None:
        self.db = db

    async def list_by_company(self, company_id: int) -> list[KioskResponse]:
        rows = (
            (
                await self.db.execute(
                    select(CompanyKiosk)
                    .where(CompanyKiosk.company_id == company_id)
                    .order_by(CompanyKiosk.id)
                )
            )
            .scalars()
            .all()
        )
        return [KioskResponse.model_validate(r) for r in rows]

    async def bind(self, req: KioskBindRequest) -> KioskResponse:
        # 회사 존재 확인
        if await self.db.get(Company, req.company_id) is None:
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

        kiosk = CompanyKiosk(company_id=req.company_id, kiosk_id=req.kiosk_id)
        self.db.add(kiosk)
        await self.db.flush()
        return KioskResponse.model_validate(kiosk)

    async def unbind(self, kiosk_id: int) -> None:
        kiosk = await self.db.get(CompanyKiosk, kiosk_id)
        if kiosk is not None:
            await self.db.delete(kiosk)
