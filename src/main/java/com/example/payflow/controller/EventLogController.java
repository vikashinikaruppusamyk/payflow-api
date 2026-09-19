package com.example.payflow.controller;

import com.example.payflow.dto.ErrorResponse;
import com.example.payflow.service.EventLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@Tag(name = "Event log", description = "Transfer life-cycle events for process mining")
@RestController
@RequestMapping("/events")
public class EventLogController {
    private final EventLogService eventLogService;

    public EventLogController(EventLogService eventLogService) {
        this.eventLogService = eventLogService;
    }

    @Operation(summary = "Export the event log as CSV (admin only)",
            description = "One row per event (case_id = transaction id, activity, timestamp, details), "
                    + "optionally limited to a time window. Suitable for loading into a process-mining tool.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "CSV file download",
                    content = @Content(mediaType = "text/csv", schema = @Schema(type = "string"), examples = @ExampleObject(
                            name = "Event log", value = """
                                    case_id,activity,timestamp,details
                                    1,INITIATED,2026-09-18T13:05:07.858694,
                                    1,VALIDATED,2026-09-18T13:05:07.874860,
                                    1,DEBITED,2026-09-18T13:05:07.875857,
                                    1,CREDITED,2026-09-18T13:05:07.876859,
                                    1,COMPLETED,2026-09-18T13:05:07.878270,
                                    2,INITIATED,2026-09-18T13:05:08.129863,
                                    2,FAILED,2026-09-18T13:05:08.137652,INSUFFICIENT_BALANCE
                                    """))),
            @ApiResponse(responseCode = "400", description = "from/to is not an ISO date-time, or from is after to",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/export")
    public ResponseEntity<String> exportCsv(
            @Parameter(description = "Only events at or after this time", example = "2026-09-01T00:00:00")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "Only events at or before this time", example = "2026-09-30T23:59:59")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        String csv = EventLogService.toCsv(eventLogService.eventsBetween(from, to));
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"payflow-event-log.csv\"")
                .body(csv);
    }
}
