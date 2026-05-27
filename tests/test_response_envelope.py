"""ApiResponse / PageResponse JSON 모양이 Java 와 동일한지 잠금."""

from __future__ import annotations

from app.core.response import ApiResponse, PageResponse


def test_api_response_void_omits_data_key() -> None:
    """Java NON_NULL — data 가 None 이면 키 생략."""
    dumped = ApiResponse.ok().model_dump(exclude_none=True, mode="json")
    assert dumped["success"] is True
    assert "data" not in dumped
    assert dumped["timestamp"].endswith("+09:00")


def test_api_response_with_data_keeps_key() -> None:
    dumped = ApiResponse.ok({"id": "abc"}).model_dump(exclude_none=True, mode="json")
    assert dumped["data"] == {"id": "abc"}


def test_page_response_shape_matches_java() -> None:
    page = PageResponse.of(["a", "b", "c"], page=0, size=10, total_elements=23)
    dumped = page.model_dump(by_alias=True, mode="json")
    # Java 필드명을 그대로 직렬화 (camelCase) — 클라이언트 무수정.
    assert set(dumped.keys()) == {
        "content",
        "page",
        "size",
        "totalElements",
        "totalPages",
        "hasNext",
        "hasPrevious",
    }
    assert dumped["totalElements"] == 23
    assert dumped["totalPages"] == 3
    assert dumped["hasNext"] is True
    assert dumped["hasPrevious"] is False
