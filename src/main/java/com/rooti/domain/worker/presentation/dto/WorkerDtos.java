package com.rooti.domain.worker.presentation.dto;

import com.rooti.domain.worker.domain.ChallengedWorker;
import com.rooti.domain.worker.domain.CompanyWorker;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class WorkerDtos {

    private WorkerDtos() {}

    @Schema(name = "WorkerResponse")
    public record Response(
            long id, long userId, String username, String name, String phoneNumber, String fcmToken) {
        public static Response from(ChallengedWorker w) {
            return new Response(
                    w.getId(),
                    w.getUser().getId(),
                    w.getUser().getUsername(),
                    w.getUser().getName(),
                    w.getUser().getPhoneNumber(),
                    w.getFcmToken());
        }
    }

    @Schema(name = "CompanyWorkerResponse")
    public record CompanyWorkerResponse(
            long id, long companyId, String companyName, Response worker, boolean hired) {
        public static CompanyWorkerResponse from(CompanyWorker cw) {
            return new CompanyWorkerResponse(
                    cw.getId(),
                    cw.getCompany().getId(),
                    cw.getCompany().getName(),
                    Response.from(cw.getWorker()),
                    cw.isHired());
        }
    }

    @Schema(name = "WorkerCreateRequest")
    public record CreateRequest(
            @NotBlank @Size(max = 150) String username,
            @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 32) String phoneNumber) {}

    @Schema(name = "HireWorkerRequest")
    public record HireRequest(@NotNull Long companyId, @NotNull Long workerId) {}
}
