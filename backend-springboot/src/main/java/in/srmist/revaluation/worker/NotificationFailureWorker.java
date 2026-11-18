package in.srmist.revaluation.worker;

import in.srmist.revaluation.dto.ProcessVariables.NotificationFailureInput;
import in.srmist.revaluation.service.EventLog;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.VariablesAsType;
import org.springframework.stereotype.Component;

/**
 * Service task "Log failed notification" (job type revaluation.log-notification-failure) in
 * notify-stakeholder.bpmn. Reached through the EMAIL_FAILED error boundary on the Email connector.
 *
 * A failed email must not stop a revaluation, so the child process records the failure here and
 * ends normally. The Exam Cell can see these entries in the timeline and resend by hand.
 */
@Component
public class NotificationFailureWorker {

    private final EventLog eventLog;

    public NotificationFailureWorker(EventLog eventLog) {
        this.eventLog = eventLog;
    }

    @JobWorker(type = "revaluation.log-notification-failure", timeout = 10_000)
    public void log(@VariablesAsType NotificationFailureInput in) {
        String to = in.recipient() == null ? "unknown" : in.recipient().role() + " <" + in.recipient().email() + ">";
        eventLog.record(in.applicationId(), in.requestId(), "WORKER",
                "Email NOT sent to " + to + " (\"" + in.subject() + "\"): " + in.emailError());
    }
}
