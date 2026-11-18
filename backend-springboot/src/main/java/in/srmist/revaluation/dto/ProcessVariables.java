package in.srmist.revaluation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * Typed versions of the process variables the job workers read and write.
 * The Camunda client converts the job's JSON variables into these records (and the returned
 * records back into variables), so the workers never handle untyped Map&lt;String,Object&gt;.
 * ignoreUnknown = true: a process may carry more variables than a worker needs.
 */
public final class ProcessVariables {

    private ProcessVariables() { }

    /** Variable "application" set by the "Register course request" script task. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CourseRequestInfo(String requestId, Integer daysSinceResult, String submittedOn) { }

    /** Variables "examiner" (revaluer) and "thirdExaminer" = {id, name, email, username}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExaminerRef(String id, String name, String email, String username) { }

    /**
     * Variable "decision" = output of DMN revaluation-outcome. grade and gradePoint come from its
     * required decision marksToGrade, so there is no separate "Determine final grade" task.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Decision(String outcome, Integer finalMarks, Boolean refundFee, String grade, Integer gradePoint) { }

    /** Variable "letter" written by the generate-letter worker. */
    public record Letter(String url, String fileName, String issuedOn) { }

    /** Variable "courseResult": the single output of every course-revaluation instance. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CourseResult(String courseCode, String courseName, String requestId, String status, String reason,
                               Integer originalMarks, Integer finalMarks, String grade, BigDecimal refundAmount,
                               String examinerName, String letterUrl) { }

    /** Variable "summary" built by the "Summarise application" script task in the parent. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Summary(Integer total, Integer changed, Integer unchanged, Integer rejected, Integer cancelled,
                          BigDecimal totalRefund, String lines) { }

    /** Variable "recipient" = one element of the sequential multi-instance "Notify stakeholders". */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Recipient(String role, String name, String email) { }

    // ---- Worker inputs (one record per job type) and outputs --------------------------------

    /** round = REVALUATION or THIRD_VALUATION (set by an input mapping on each "Email ... login" task). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SendLoginInput(String applicationId, String courseCode, String selectedExaminerId, String round,
                                 CourseRequestInfo application) { }

    /** The task's output mapping copies assignedExaminer into "examiner" or "thirdExaminer". */
    public record SendLoginOutput(ExaminerRef assignedExaminer) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GenerateLetterInput(String applicationId, String registerNo, String studentName, String courseCode,
                                      String courseName, Integer originalMarks, String originalGrade,
                                      Decision decision, Boolean thirdValuationDone,
                                      String refundId, String resultUpdatedAt, CourseRequestInfo application) { }

    public record GenerateLetterOutput(Letter letter) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SaveResultsInput(String applicationId, List<CourseResult> courseResults, Summary summary) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NotificationFailureInput(String applicationId, String requestId, Recipient recipient,
                                           String subject, String emailError) { }
}
