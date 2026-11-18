package in.srmist.revaluation.worker;

import in.srmist.revaluation.config.RevaluationProperties;
import in.srmist.revaluation.dto.ProcessVariables.ExaminerRef;
import in.srmist.revaluation.dto.ProcessVariables.SendLoginInput;
import in.srmist.revaluation.dto.ProcessVariables.SendLoginOutput;
import in.srmist.revaluation.entity.Examiner;
import in.srmist.revaluation.service.AuthService;
import in.srmist.revaluation.service.AuthService.Credentials;
import in.srmist.revaluation.service.EventLog;
import in.srmist.revaluation.service.ExaminerService;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.VariablesAsType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Service tasks "Email examiner login" and "Email third examiner login"
 * (job type revaluation.send-examiner-login) in course-revaluation.bpmn.
 *
 * Runs right after the CoE picks an examiner:
 *  1. records the assignment (workload + course row);
 *  2. creates the examiner's portal account, or gives it a new temporary password;
 *  3. emails the username and temporary password to the examiner;
 *  4. returns assignedExaminer = {id, name, email, username}. The task's output mapping copies it
 *     into "examiner" or "thirdExaminer", and the next user task is assigned to that username.
 *
 * The password goes only into the email. It is never a process variable, so it does not appear
 * in Operate or in the job logs.
 *
 * If the mail server is down, the exception fails the job: Camunda retries it (3 attempts) and
 * then raises an incident, the same as the letter worker. A retry issues a fresh password, and the
 * newest email is the valid one.
 */
@Component
public class SendExaminerLoginWorker {

    private final ExaminerService examiners;
    private final AuthService auth;
    private final JavaMailSender mail;
    private final EventLog eventLog;
    private final RevaluationProperties props;

    public SendExaminerLoginWorker(ExaminerService examiners, AuthService auth, JavaMailSender mail,
                                   EventLog eventLog, RevaluationProperties props) {
        this.examiners = examiners;
        this.auth = auth;
        this.mail = mail;
        this.eventLog = eventLog;
        this.props = props;
    }

    @JobWorker(type = "revaluation.send-examiner-login", timeout = 30_000, maxJobsActive = 8, retryBackoff = 10_000)
    public SendLoginOutput send(@VariablesAsType SendLoginInput in) {
        String requestId = in.application().requestId();
        Examiner examiner = examiners.recordAssignment(in.applicationId(), in.courseCode(), requestId, in.selectedExaminerId(), in.round());
        Credentials login = auth.issueTemporaryPassword(examiner);
        boolean third = ExaminerService.THIRD_VALUATION.equals(in.round());

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(props.mailFrom());
        message.setTo(examiner.getEmail());
        message.setSubject((third ? "Third valuation" : "Revaluation") + " assigned - " + in.courseCode() + " (" + requestId + ")");
        message.setText("Dear " + examiner.getName() + ",\n\n"
                + "The Controller of Examinations has assigned you the " + (third ? "third valuation" : "revaluation")
                + " of an answer script for " + in.courseCode() + " (request " + requestId + ").\n"
                + "The original marks are hidden from you to keep the valuation unbiased. Please finish within 72 hours.\n\n"
                + "Sign in to the staff portal: " + props.frontendOrigin() + "/login\n"
                + "Username: " + login.username() + "\n"
                + "Temporary password: " + login.password() + "\n"
                + "(This password replaces any earlier one sent to you.)\n\n"
                + "Controller of Examinations");
        mail.send(message);

        eventLog.record(in.applicationId(), requestId, "WORKER",
                in.courseCode() + ": portal login emailed to " + examiner.getName() + " <" + examiner.getEmail() + ">");
        return new SendLoginOutput(new ExaminerRef(examiner.getExaminerId(), examiner.getName(), examiner.getEmail(), login.username()));
    }
}
