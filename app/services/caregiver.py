"""Caregiver service."""

from __future__ import annotations

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.exceptions import AuthForbiddenException, BusinessException, ErrorCode
from app.models import Caregiver, CaregiverWorkerRelation, ChallengedWorker
from app.schemas.caregiver import CaregiverRelationResponse, CaregiverResponse


class CaregiverService:
    def __init__(self, db: AsyncSession) -> None:
        self.db = db

    async def get_by_user_id(self, user_id: int) -> Caregiver:
        result = await self.db.execute(select(Caregiver).where(Caregiver.user_id == user_id))
        caregiver = result.scalar_one_or_none()
        if caregiver is None:
            raise BusinessException(ErrorCode.CAREGIVER_NOT_FOUND)
        return caregiver

    async def me(self, user_id: int) -> CaregiverResponse:
        return CaregiverResponse.model_validate(await self.get_by_user_id(user_id))

    async def register_worker(self, caregiver_user_id: int, worker_id: int) -> CaregiverRelationResponse:
        caregiver = await self.get_by_user_id(caregiver_user_id)

        dup = (
            await self.db.execute(
                select(CaregiverWorkerRelation.id).where(
                    CaregiverWorkerRelation.caregiver_id == caregiver.id,
                    CaregiverWorkerRelation.challenged_worker_id == worker_id,
                )
            )
        ).first()
        if dup:
            raise BusinessException(ErrorCode.CAREGIVER_RELATION_DUPLICATED)

        worker = await self.db.get(ChallengedWorker, worker_id)
        if worker is None:
            raise BusinessException(ErrorCode.WORKER_NOT_FOUND)

        relation = CaregiverWorkerRelation(
            caregiver_id=caregiver.id, challenged_worker_id=worker.id
        )
        self.db.add(relation)
        await self.db.flush()
        return CaregiverRelationResponse.model_validate(relation)

    async def list_relations(self, caregiver_user_id: int) -> list[CaregiverRelationResponse]:
        caregiver = await self.get_by_user_id(caregiver_user_id)
        rows = (
            (
                await self.db.execute(
                    select(CaregiverWorkerRelation)
                    .where(CaregiverWorkerRelation.caregiver_id == caregiver.id)
                    .order_by(CaregiverWorkerRelation.id.desc())
                )
            )
            .scalars()
            .all()
        )
        return [CaregiverRelationResponse.model_validate(r) for r in rows]

    async def remove_relation(self, caregiver_user_id: int, relation_id: int) -> None:
        caregiver = await self.get_by_user_id(caregiver_user_id)
        relation = await self.db.get(CaregiverWorkerRelation, relation_id)
        if relation is None:
            raise BusinessException(ErrorCode.CAREGIVER_NOT_FOUND)
        if relation.caregiver_id != caregiver.id:
            raise AuthForbiddenException("relation does not belong to caregiver")
        await self.db.delete(relation)
