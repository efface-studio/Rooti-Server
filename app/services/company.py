"""Company service. 단건 조회는 Redis 캐싱 (Java @Cacheable 등가)."""

from __future__ import annotations

from redis.asyncio import Redis
from sqlalchemy import func, or_, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core import cache
from app.core.exceptions import BusinessException, ErrorCode
from app.core.pagination import Page, PageParams
from app.core.security import PrincipalDetails
from app.models import Company
from app.schemas.company import (
    CompanyCreateRequest,
    CompanyResponse,
    CompanyUpdateRequest,
)

_CACHE_NS = "company"
_CACHE_TTL = 600  # 10 min — Java CacheConfig.COMPANIES 와 동일


class CompanyService:
    def __init__(self, db: AsyncSession, redis: Redis | None = None) -> None:
        self.db = db
        self.redis = redis

    async def get_or_throw(self, company_id: int) -> Company:
        company = await self.db.get(Company, company_id)
        if company is None:
            raise BusinessException(ErrorCode.COMPANY_NOT_FOUND)
        return company

    async def get(self, company_id: int) -> CompanyResponse:
        # Cache hit → 즉시 반환. miss → DB 조회 후 set.
        if self.redis is not None:
            hit = await cache.get_model(self.redis, CompanyResponse, _CACHE_NS, company_id)
            if hit is not None:
                return hit
        resp = CompanyResponse.model_validate(await self.get_or_throw(company_id))
        if self.redis is not None:
            await cache.set_model(self.redis, resp, _CACHE_NS, company_id, ttl_seconds=_CACHE_TTL)
        return resp

    async def search(self, keyword: str | None, params: PageParams) -> Page[CompanyResponse]:
        base = select(Company).where(Company.use_flag.is_(True))
        if keyword:
            ilike = f"%{keyword.strip()}%"
            base = base.where(or_(Company.name.ilike(ilike), Company.location.ilike(ilike)))

        total_q = select(func.count()).select_from(base.subquery())
        total = int((await self.db.execute(total_q)).scalar_one() or 0)

        page_q = base.order_by(Company.id.desc()).offset(params.offset).limit(params.limit)
        rows = (await self.db.execute(page_q)).scalars().all()

        return Page.build(
            [CompanyResponse.model_validate(r) for r in rows],
            params=params,
            total_elements=total,
        )

    async def create(
        self, req: CompanyCreateRequest, actor: PrincipalDetails | None = None
    ) -> CompanyResponse:
        dup = (await self.db.execute(select(Company.id).where(Company.name == req.name))).first()
        if dup:
            raise BusinessException(ErrorCode.CONFLICT, "동일한 이름의 회사가 이미 존재합니다.")

        company = Company(
            name=req.name,
            location=req.location,
            use_flag=True,
            image_path=req.image_path,
            template_id=req.template_id,
            template_data=req.template_data,
            created_by=actor.user_id if actor else None,
            updated_by=actor.user_id if actor else None,
        )
        self.db.add(company)
        await self.db.flush()
        return CompanyResponse.model_validate(company)

    async def update(
        self,
        company_id: int,
        req: CompanyUpdateRequest,
        actor: PrincipalDetails | None = None,
    ) -> CompanyResponse:
        company = await self.get_or_throw(company_id)
        if req.name is not None:
            company.name = req.name
        if req.location is not None:
            company.location = req.location
        if req.image_path is not None:
            company.image_path = req.image_path
        if req.template_id is not None:
            company.template_id = req.template_id
        if req.template_data is not None:
            company.template_data = req.template_data
        if actor:
            company.updated_by = actor.user_id
        if self.redis is not None:
            await cache.evict(self.redis, _CACHE_NS, company_id)
        return CompanyResponse.model_validate(company)

    async def deactivate(self, company_id: int, actor: PrincipalDetails | None = None) -> None:
        company = await self.get_or_throw(company_id)
        company.use_flag = False
        if actor:
            company.updated_by = actor.user_id
        if self.redis is not None:
            await cache.evict(self.redis, _CACHE_NS, company_id)
