package in.srmist.revaluation.controller;

import in.srmist.revaluation.dto.ApplicationRequest;
import in.srmist.revaluation.dto.Views.ApplicationCreated;
import in.srmist.revaluation.dto.Views.ApplicationDetail;
import in.srmist.revaluation.dto.Views.ApplicationSummary;
import in.srmist.revaluation.dto.Views.TimelineEntry;
import in.srmist.revaluation.service.ApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Student side: apply, list applications, live status and timeline. Controllers only call services. */
@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService service;

    public ApplicationController(ApplicationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationCreated submit(@Valid @RequestBody ApplicationRequest request) {
        return service.submit(request);
    }

    @GetMapping
    public List<ApplicationSummary> list() {
        return service.list();
    }

    @GetMapping("/{applicationId}")
    public ApplicationDetail detail(@PathVariable String applicationId) {
        return service.detail(applicationId);
    }

    @GetMapping("/{applicationId}/timeline")
    public List<TimelineEntry> timeline(@PathVariable String applicationId) {
        return service.timeline(applicationId);
    }
}
