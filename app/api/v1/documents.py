"""Caregiver document endpoints — upload/list/download/delete."""

from __future__ import annotations

from typing import Annotated
from urllib.parse import quote

from fastapi import Depends, File, Form, UploadFile
from fastapi.responses import StreamingResponse

from app.api.deps import CurrentUser, DbSession
from app.core.response import ApiResponse
from app.core.router import RootiRouter
from app.core.upload_validation import sanitize_filename, validate_document_upload
from app.integrations.storage import StorageService, get_storage
from app.schemas.document import DocumentResponse
from app.services.document import CaregiverDocumentService

router = RootiRouter(tags=["document"])


def _service(
    db: DbSession, storage: Annotated[StorageService, Depends(get_storage)]
) -> CaregiverDocumentService:
    return CaregiverDocumentService(db, storage)


DocSvc = Annotated[CaregiverDocumentService, Depends(_service)]


@router.post("", summary="Upload a caregiver document")
async def upload(
    me: CurrentUser,
    svc: DocSvc,
    relation_id: int = Form(alias="relationId"),
    type_id: int = Form(alias="typeId"),
    file: UploadFile = File(...),
) -> ApiResponse[DocumentResponse]:
    # 크기/MIME/파일명 게이트 — 서비스 진입 전 단일 검증.
    validate_document_upload(file)
    return ApiResponse.ok(
        await svc.upload(
            actor_user_id=me.user_id,
            relation_id=relation_id,
            type_id=type_id,
            original_filename=sanitize_filename(file.filename),
            file=file.file,
            size=file.size or 0,
            content_type=file.content_type,
        )
    )


@router.get("/by-relation/{relation_id}")
async def list_by_relation(
    relation_id: int, svc: DocSvc, _: CurrentUser
) -> ApiResponse[list[DocumentResponse]]:
    return ApiResponse.ok(await svc.list_by_relation(relation_id))


@router.get("/{document_id}/download")
async def download(
    document_id: int,
    me: CurrentUser,
    svc: DocSvc,
    storage: Annotated[StorageService, Depends(get_storage)],
) -> StreamingResponse:
    doc = await svc.load_for_download(me.user_id, document_id)
    stream = await storage.open(doc.filename)
    name = doc.filename.rsplit("/", 1)[-1]
    encoded = quote(name, safe="")
    headers = {"content-disposition": f"attachment; filename*=UTF-8''{encoded}"}
    return StreamingResponse(
        stream,
        media_type=doc.content_type or "application/octet-stream",
        headers=headers,
    )


@router.delete("/{document_id}")
async def delete(
    document_id: int, me: CurrentUser, svc: DocSvc
) -> ApiResponse[None]:
    await svc.delete(me.user_id, document_id)
    return ApiResponse.ok()
