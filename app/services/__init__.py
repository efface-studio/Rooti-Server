"""도메인 서비스 (비즈니스 로직).

각 서비스는 `__init__(self, db: AsyncSession, ...)` 형태. FastAPI 의 Depends 로
생성/주입하는 게 기본 패턴 — app/api/deps.py 참조.
"""
