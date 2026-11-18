package in.srmist.revaluation.worker;

import in.srmist.revaluation.config.RevaluationProperties;
import in.srmist.revaluation.dto.ProcessVariables.CourseRequestInfo;
import in.srmist.revaluation.dto.ProcessVariables.SendLoginInput;
import in.srmist.revaluation.dto.ProcessVariables.SendLoginOutput;
import in.srmist.revaluation.entity.Examiner;
import in.srmist.revaluation.service.AuthService;
import in.srmist.revaluation.service.EventLog;
import in.srmist.revaluation.service.ExaminerService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** The login email after the CoE assigns an examiner. Mail server, database and Camunda are mocks. */
class SendExaminerLoginWorkerTest {

    private final ExaminerService examiners = mock(ExaminerService.class);
    private final AuthService auth = mock(AuthService.class);
    private final JavaMailSender mail = mock(JavaMailSender.class);
    private final RevaluationProperties props = new RevaluationProperties(null, null, null, null, null, null, null,
            "coe@srmist.test", "http://localhost:5173");
    private final SendExaminerLoginWorker worker = new SendExaminerLoginWorker(examiners, auth, mail, mock(EventLog.class), props);

    @Test
    void emailsTheLoginAndReturnsTheUsernameButNotThePassword() {
        Examiner ravi = new Examiner("EXM-103", "Dr. C. Ravi", "c.ravi@examiners.test", "Mathematics", "21CSC305P", 1);
        when(examiners.recordAssignment("APP-1", "21CSC305P", "REV-1", "EXM-103", "THIRD_VALUATION")).thenReturn(ravi);
        when(auth.issueTemporaryPassword(ravi)).thenReturn(new AuthService.Credentials("exm-103", "Xk7pQ2mW9a"));

        SendLoginOutput out = worker.send(new SendLoginInput("APP-1", "21CSC305P", "EXM-103", "THIRD_VALUATION",
                new CourseRequestInfo("REV-1", 2, "2026-11-18")));

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(sent.capture());
        assertThat(sent.getValue().getTo()).containsExactly("c.ravi@examiners.test");
        assertThat(sent.getValue().getSubject()).startsWith("Third valuation assigned - 21CSC305P");
        assertThat(sent.getValue().getText()).contains("Username: exm-103", "Temporary password: Xk7pQ2mW9a", "http://localhost:5173/login");
        // The process variable carries the username only; the password must never reach Camunda
        assertThat(out.assignedExaminer().username()).isEqualTo("exm-103");
        assertThat(out.toString()).doesNotContain("Xk7pQ2mW9a");
    }
}
