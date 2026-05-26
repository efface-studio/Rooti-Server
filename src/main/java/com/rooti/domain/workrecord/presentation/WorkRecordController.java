package com.rooti.domain.workrecord.presentation;

import com.rooti.domain.workrecord.application.WorkRecordService;
import com.rooti.domain.workrecord.presentation.dto.EndRequest;
import com.rooti.domain.workrecord.presentation.dto.ProcessEndRequest;
import com.rooti.domain.workrecord.presentation.dto.ProcessResponse;
import com.rooti.domain.workrecord.presentation.dto.ProcessStartRequest;
import com.rooti.domain.workrecord.presentation.dto.RecordResponse;
import com.rooti.domain.workrecord.presentation.dto.StartRequest;
import com.rooti.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/work-records")
@RequiredArgsConstructor
@Tag(name = "WorkRecord")
@SecurityRequirement(name = "bearerAuth")
public class WorkRecordController {

    private final WorkRecordService workRecordService;

    @PostMapping("/begin")
    public ApiResponse<RecordResponse> begin(@Valid @RequestBody StartRequest req) {
        return ApiResponse.ok(workRecordService.begin(req));
    }

    @PostMapping("/end")
    public ApiResponse<RecordResponse> end(@Valid @RequestBody EndRequest req) {
        return ApiResponse.ok(workRecordService.end(req));
    }

    @GetMapping("/by-schedule/{scheduleId}")
    public ApiResponse<List<RecordResponse>> list(@PathVariable long scheduleId) {
        return ApiResponse.ok(workRecordService.list(scheduleId));
    }

    @PostMapping("/processes/begin")
    public ApiResponse<ProcessResponse> beginProcess(@Valid @RequestBody ProcessStartRequest req) {
        return ApiResponse.ok(workRecordService.beginProcess(req));
    }

    @PostMapping("/processes/end")
    public ApiResponse<ProcessResponse> endProcess(@Valid @RequestBody ProcessEndRequest req) {
        return ApiResponse.ok(workRecordService.endProcess(req));
    }

    @GetMapping("/processes/by-schedule/{scheduleId}")
    public ApiResponse<List<ProcessResponse>> listProcess(@PathVariable long scheduleId) {
        return ApiResponse.ok(workRecordService.listProcess(scheduleId));
    }
}
