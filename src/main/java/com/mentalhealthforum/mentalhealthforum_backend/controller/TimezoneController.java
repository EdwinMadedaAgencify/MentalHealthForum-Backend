package com.mentalhealthforum.mentalhealthforum_backend.controller;

import com.mentalhealthforum.mentalhealthforum_backend.dto.StandardSuccessResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.timezone.TimezonesResponse;
import com.mentalhealthforum.mentalhealthforum_backend.service.TimezoneService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/timezones")
public class TimezoneController {

    private final TimezoneService timezoneService;

    public TimezoneController(TimezoneService timezoneService) {
        this.timezoneService = timezoneService;
    }

    @GetMapping
    public ResponseEntity<StandardSuccessResponse<TimezonesResponse>> getTimezones(
            @RequestParam(defaultValue = "grouped") String format
    ){
        TimezonesResponse response = "flat".equalsIgnoreCase(format)
                ? timezoneService.getTimezonesFlat()
                : timezoneService.getTimezonesGrouped();

        return ResponseEntity.ok(new StandardSuccessResponse<>(
                "Timezones retrieved successfully",
                response
        ));
    }
}
