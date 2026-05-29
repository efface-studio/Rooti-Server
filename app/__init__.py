"""Rooti backend — FastAPI service (migrated from the Spring Boot server).

The migration is complete: the whole application lives under `app/`, the
PostgreSQL schema is in `migrations/V*.sql`, and the HTTP API contract is
unchanged so the React client (`Rooti-Client/`) needs no changes.
"""

__version__ = "1.0.0"
