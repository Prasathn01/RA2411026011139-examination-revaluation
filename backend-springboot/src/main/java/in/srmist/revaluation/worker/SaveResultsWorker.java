package in.srmist.revaluation.worker;

import in.srmist.revaluation.dto.ProcessVariables.SaveResultsInput;
import in.srmist.revaluation.service.ApplicationService;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.VariablesAsType;
import org.springframework.stereotype.Component;

/**
 * Service task "Save results" (job type revaluation.save-results) in the parent process.
 * Runs after the parallel multi-instance has collected every courseResult into courseResults,
 * and writes the final marks, grades and refunds into the CourseRequest rows.
 * Saving is an overwrite of the same rows, so a retried job gives the same result.
 */
@Component
public class SaveResultsWorker {

    private final ApplicationService applications;

    public SaveResultsWorker(ApplicationService applications) {
        this.applications = applications;
    }

    @JobWorker(type = "revaluation.save-results", timeout = 30_000, retryBackoff = 10_000)
    public void save(@VariablesAsType SaveResultsInput in) {
        applications.saveResults(in);
    }
}
