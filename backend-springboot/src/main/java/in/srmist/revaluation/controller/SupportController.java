package in.srmist.revaluation.controller;

import in.srmist.revaluation.dto.Views.Message;
import in.srmist.revaluation.entity.Examiner;
import in.srmist.revaluation.service.ExaminerService;
import in.srmist.revaluation.service.PaymentService;
import in.srmist.revaluation.exception.ApiException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/** Examiner directory and the demo payment helper. */
@RestController
@RequestMapping("/api")
public class SupportController {

    private final ExaminerService examiners;
    private final PaymentService payments;

    public SupportController(ExaminerService examiners, PaymentService payments) {
        this.examiners = examiners;
        this.payments = payments;
    }

    @GetMapping("/examiners")
    public List<Examiner> examiners() {
        return examiners.all();
    }

    /** BPMN XML of one of our processes, for the bpmn-js diagram in the tracker. Only our three files are served. */
    @GetMapping(value = "/models/{processId}", produces = MediaType.APPLICATION_XML_VALUE)
    public String model(@PathVariable String processId) throws IOException {
        if (!Set.of("revaluation-application", "course-revaluation", "notify-stakeholder").contains(processId)) {
            throw ApiException.notFound("Unknown process " + processId);
        }
        return new ClassPathResource("models/" + processId + ".bpmn").getContentAsString(StandardCharsets.UTF_8);
    }

    /** Demo only: pays the test payment link of one course so the demo does not need the mock dashboard. */
    @PostMapping("/payments/{requestId}/simulate")
    public Message simulatePayment(@PathVariable String requestId) {
        return new Message(payments.simulate(requestId));
    }
}
