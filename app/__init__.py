"""Rooti backend (FastAPI port of the Spring Boot server).

Migration plan: see README.python.md. Domains under `app/domain/` are added one
at a time; the corresponding Java package under `src/main/java/com/rooti/domain/`
is deleted only after the Python version reaches API/test parity.
"""

__version__ = "1.0.0"
