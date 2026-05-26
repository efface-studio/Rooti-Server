package com.rooti.domain.board.application;

import com.rooti.domain.board.domain.CaregiverBoard;
import com.rooti.domain.board.infrastructure.CaregiverBoardRepository;
import com.rooti.domain.board.presentation.dto.Response;
import com.rooti.domain.board.presentation.dto.WriteRequest;
import com.rooti.domain.user.application.UserQueryService;
import com.rooti.domain.user.domain.User;
import com.rooti.global.exception.BusinessException;
import com.rooti.global.exception.ErrorCode;
import com.rooti.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CaregiverBoardService {

    private final CaregiverBoardRepository boardRepository;
    private final HtmlSanitizer sanitizer;
    private final UserQueryService userQueryService;

    public Response create(long authorUserId, WriteRequest req) {
        User author = userQueryService.getById(authorUserId);
        CaregiverBoard b =
                CaregiverBoard.builder()
                        .author(author)
                        .title(req.title())
                        .body(sanitizer.sanitize(req.body()))
                        .published(req.published())
                        .build();
        return Response.from(boardRepository.save(b));
    }

    public Response update(long authorUserId, long boardId, WriteRequest req) {
        CaregiverBoard b = getOrThrow(boardId);
        if (b.getAuthor().getId() != authorUserId) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
        }
        b.edit(req.title(), sanitizer.sanitize(req.body()));
        if (req.published() != null) {
            if (req.published()) b.publish();
            else b.unpublish();
        }
        return Response.from(b);
    }

    public void delete(long authorUserId, long boardId) {
        CaregiverBoard b = getOrThrow(boardId);
        if (b.getAuthor().getId() != authorUserId) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
        }
        boardRepository.delete(b);
    }

    @Transactional(readOnly = true)
    public Response get(long id) {
        return Response.from(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<Response> list(String keyword, Pageable pageable) {
        return PageResponse.of(boardRepository.searchPublished(keyword, pageable).map(Response::from));
    }

    private CaregiverBoard getOrThrow(long id) {
        return boardRepository
                .findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
    }
}
