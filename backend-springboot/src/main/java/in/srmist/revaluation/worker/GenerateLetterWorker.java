package in.srmist.revaluation.worker;

import in.srmist.revaluation.dto.ProcessVariables.GenerateLetterInput;
import in.srmist.revaluation.dto.ProcessVariables.GenerateLetterOutput;
import in.srmist.revaluation.service.LetterService;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.VariablesAsType;
import org.springframework.stereotype.Component;

/**
 * Service task "Generate outcome letter" (job type revaluation.generate-letter).
 *
 * No BPMN error here on purpose: if the document store is down, that is a technical problem,
 * not a business outcome. The exception fails the job; Camunda retries after 10 s (3 attempts in
 * total, set by retries="3" in the BPMN) and then shows an incident in Operate. After the store is
 * back, "Retry" on the incident continues the process. This is the incident demo (scenario 9).
 */
@Component
public class GenerateLetterWorker {

    private final LetterService letters;

    public GenerateLetterWorker(LetterService letters) {
        this.letters = letters;
    }

    @JobWorker(type = "revaluation.generate-letter", timeout = 30_000, maxJobsActive = 8, retryBackoff = 10_000)
    public GenerateLetterOutput generate(@VariablesAsType GenerateLetterInput in) {
        return new GenerateLetterOutput(letters.generate(in));
    }
}
