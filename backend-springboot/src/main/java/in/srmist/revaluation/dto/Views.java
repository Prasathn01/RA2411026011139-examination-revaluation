package in.srmist.revaluation.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** Response bodies of the REST API (JSON shapes the React portal reads). */
public final class Views {

    private Views() { }

    /** Answer to POST /api/applications. */
    public record ApplicationCreated(String applicationId, Long processInstanceKey) { }

    /** One row of GET /api/applications. */
    public record ApplicationSummary(String applicationId, String registerNo, String studentName,
                                     String status, List<String> courses, Instant createdAt) { }

    /** GET /api/applications/{applicationId}: database row + live state of every course from Camunda. */
    public record ApplicationDetail(String applicationId, String registerNo, String studentName, String studentEmail,
                                    String status, Long processInstanceKey, BigDecimal totalRefund, Instant createdAt,
                                    String parentState, List<CourseView> courses) { }

    /**
     * One course card in the live tracker. "stage" is the step the course-revaluation instance
     * is currently in (worked out from its active elements in Camunda).
     */
    public record CourseView(String courseCode, String courseName, String requestId, Long processInstanceKey,
                             String stage, String status, boolean incident, boolean escalated,
                             String paymentUrl, boolean paymentPending, String examinerName,
                             Integer originalMarks, Integer finalMarks, String grade, BigDecimal refundAmount,
                             String reason, String letterUrl, List<String> activeElements) { }

    /** One line of GET /api/applications/{id}/timeline. source = CAMUNDA (engine history) or a ProcessEvent type. */
    public record TimelineEntry(OffsetDateTime time, String source, String courseCode, String label, String state) { }

    /** One row of the custom task inbox. */
    public record TaskView(Long taskKey, String name, String elementId, String courseCode, String requestId,
                           List<String> candidateGroups, String assignee, OffsetDateTime createdAt, boolean escalated) { }

    /** GET /api/tasks/{taskKey}: the task plus only the variables its form is allowed to show. */
    public record TaskDetail(TaskView task, Map<String, Object> variables) { }

    /** One examiner the CoE may pick in "CoE assigns examiner". */
    public record ExaminerOption(String examinerId, String name, String department, int activeAssignments) { }

    /** POST /api/auth/login and GET /api/auth/me (token is null for /me). role = COE or EXAMINER. */
    public record LoginResponse(String token, String username, String displayName, String role) { }

    public record Message(String message) { }
}
