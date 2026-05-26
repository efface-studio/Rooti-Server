package com.rooti.domain.board.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "BoardWriteRequest")
public record WriteRequest(
        @NotBlank @Size(max = 255) String title, @NotBlank String body, Boolean published) {}
