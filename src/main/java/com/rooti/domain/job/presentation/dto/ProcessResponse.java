package com.rooti.domain.job.presentation.dto;

import com.rooti.domain.job.domain.JobProcess;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(name = "JobProcessResponse")
public record ProcessResponse(
        long id,
        String name,
        int sequence,
        String videoPath,
        String startMessage,
        String endMessage,
        Map<String, Object> context,
        int processTimeSeconds) {
    public static ProcessResponse from(JobProcess p) {
        return new ProcessResponse(
                p.getId(),
                p.getName(),
                p.getSequence(),
                p.getVideoPath(),
                p.getStartMessage(),
                p.getEndMessage(),
                p.getContext(),
                p.getProcessTimeSeconds());
    }
}
