package com.rooti.domain.board.presentation;

import com.rooti.domain.board.application.CaregiverBoardService;
import com.rooti.domain.board.presentation.dto.BoardDtos.Response;
import com.rooti.domain.board.presentation.dto.BoardDtos.WriteRequest;
import com.rooti.global.response.ApiResponse;
import com.rooti.global.response.PageResponse;
import com.rooti.global.security.CurrentUser;
import com.rooti.global.security.PrincipalDetails;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/boards")
@RequiredArgsConstructor
@Tag(name = "Board")
@SecurityRequirement(name = "bearerAuth")
public class CaregiverBoardController {

    private final CaregiverBoardService boardService;

    @GetMapping
    public ApiResponse<PageResponse<Response>> list(
            @RequestParam(required = false) String keyword, @ParameterObject Pageable pageable) {
        return ApiResponse.ok(boardService.list(keyword, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<Response> get(@PathVariable long id) {
        return ApiResponse.ok(boardService.get(id));
    }

    @PostMapping
    public ApiResponse<Response> create(
            @CurrentUser PrincipalDetails me, @Valid @RequestBody WriteRequest req) {
        return ApiResponse.ok(boardService.create(me.userId(), req));
    }

    @PatchMapping("/{id}")
    public ApiResponse<Response> update(
            @CurrentUser PrincipalDetails me,
            @PathVariable long id,
            @Valid @RequestBody WriteRequest req) {
        return ApiResponse.ok(boardService.update(me.userId(), id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@CurrentUser PrincipalDetails me, @PathVariable long id) {
        boardService.delete(me.userId(), id);
        return ApiResponse.ok();
    }
}
