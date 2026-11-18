package in.srmist.revaluation.service;

import com.fasterxml.jackson.databind.JsonNode;
import in.srmist.revaluation.config.RevaluationProperties;
import in.srmist.revaluation.dto.ApplicationRequest;
import in.srmist.revaluation.dto.ProcessVariables.CourseResult;
import in.srmist.revaluation.dto.ProcessVariables.SaveResultsInput;
import in.srmist.revaluation.dto.Views.ApplicationCreated;
import in.srmist.revaluation.dto.Views.ApplicationDetail;
import in.srmist.revaluation.dto.Views.ApplicationSummary;
import in.srmist.revaluation.dto.Views.CourseView;
import in.srmist.revaluation.dto.Views.TimelineEntry;
import in.srmist.revaluation.entity.CourseRequest;
import in.srmist.revaluation.entity.RevaluationApplication;
import in.srmist.revaluation.entity.RevaluationApplication.Status;
import in.srmist.revaluation.exception.ApiException;
import in.srmist.revaluation.repository.ProcessEventRepository;
import in.srmist.revaluation.repository.RevaluationApplicationRepository;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.ProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Business logic for applications: submit (start the process), list, live detail and timeline. */
@Service
public class ApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);
    private static final DateTimeFormatter ID_FORMAT = DateTimeFormatter.ofPattern("yyMMddHHmmssSSS");

    /**
     * Element ID in course-revaluation.bpmn -> stage label shown on the tracker card.
     * Order matters: when several elements are active, the first match in this list wins.
     */
    private static final Map<String, String> STAGES = new LinkedHashMap<>();
    static {
        STAGES.put("Task_ThirdValuation", "Third valuation");
        STAGES.put("Task_Revalue", "Under revaluation");
        STAGES.put("Task_SendLoginThird", "Third valuation");
        STAGES.put("Task_CoeAssignThird", "Waiting for CoE to assign third examiner");
        STAGES.put("Task_SendLogin", "Examiner assigned");
        STAGES.put("Task_CoeAssign", "Waiting for CoE to assign examiner");
        STAGES.put("Catch_FeePaid", "Awaiting payment");
        STAGES.put("Task_PaymentLink", "Creating payment link");
        STAGES.put("Task_FetchResult", "Fetching result");
        STAGES.put("Task_Register", "Checking eligibility");
        STAGES.put("Task_Eligibility", "Checking eligibility");
        STAGES.put("Task_CompareMarks", "Deciding outcome");
        STAGES.put("Task_DecideOutcome", "Deciding outcome");
        STAGES.put("Task_PublishResult", "Publishing result");
        STAGES.put("Task_ManualUpdate", "Waiting for CoE to update result");
        STAGES.put("Task_Refund", "Refunding fee");
        STAGES.put("Task_GenerateLetter", "Generating letter");
        STAGES.put("Task_RecordOutcome", "Notifying student");
        STAGES.put("Call_NotifyOutcome", "Notifying student");
        STAGES.put("Call_NotifyRejection", "Notifying student");
        STAGES.put("Call_NotifyCancelled", "Notifying student");
    }

    private final RevaluationApplicationRepository applications;
    private final ProcessEventRepository events;
    private final CamundaClient camunda;
    private final CamundaQueryService query;
    private final EventLog eventLog;
    private final RevaluationProperties props;

    public ApplicationService(RevaluationApplicationRepository applications, ProcessEventRepository events,
                              CamundaClient camunda, CamundaQueryService query, EventLog eventLog,
                              RevaluationProperties props) {
        this.applications = applications;
        this.events = events;
        this.camunda = camunda;
        this.query = query;
        this.eventLog = eventLog;
        this.props = props;
    }

    /**
     * Saves the application, then starts process "revaluation-application" with the start variables.
     * The courses list is passed as-is: the parallel multi-instance creates one child per entry.
     */
    @Transactional
    public ApplicationCreated submit(ApplicationRequest req) {
        String problem = CourseListValidator.check(req.courses());
        if (problem != null) {
            throw ApiException.badRequest(problem);
        }

        RevaluationApplication app = new RevaluationApplication();
        app.setApplicationId("APP-" + LocalDateTime.now(ZoneOffset.ofHoursMinutes(5, 30)).format(ID_FORMAT));
        app.setRegisterNo(req.registerNo());
        app.setStudentName(req.studentName().trim());
        app.setStudentEmail(req.studentEmail().trim());
        for (String code : req.courses()) {
            CourseRequest course = new CourseRequest();
            course.setCourseCode(code);
            app.addCourse(course);
        }
        applications.save(app);

        // HashMap (not Map.of) because the optional demo overrides may be null.
        Map<String, Object> variables = new HashMap<>();
        variables.put("applicationId", app.getApplicationId());
        variables.put("registerNo", app.getRegisterNo());
        variables.put("studentName", app.getStudentName());
        variables.put("studentEmail", app.getStudentEmail());
        variables.put("courses", req.courses());
        variables.put("paymentWindow", req.paymentWindow());
        variables.put("examinerDeadline", req.examinerDeadline());
        variables.put("hodEmail", props.hodEmail());
        variables.put("coeEmail", props.coeEmail());

        try {
            long key = camunda.newCreateInstanceCommand()
                    .bpmnProcessId("revaluation-application")
                    .latestVersion()
                    .variables(variables)
                    .send().join()
                    .getProcessInstanceKey();
            app.setProcessInstanceKey(key);
            app.setStatus(Status.IN_PROGRESS);
            eventLog.record(app.getApplicationId(), null, "BACKEND",
                    "Application submitted for " + req.courses().size() + " course(s); process instance " + key + " started");
            return new ApplicationCreated(app.getApplicationId(), key);
        } catch (RuntimeException e) {
            log.error("Could not start the process for {}", app.getApplicationId(), e);
            app.setStatus(Status.FAILED_TO_START);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The application was saved but the workflow engine could not be reached. Please try again in a minute.");
        }
    }

    @Transactional(readOnly = true)
    public List<ApplicationSummary> list() {
        return applications.findAllByOrderByCreatedAtDesc().stream()
                .map(a -> new ApplicationSummary(a.getApplicationId(), a.getRegisterNo(), a.getStudentName(),
                        a.getStatus().name(), a.getCourses().stream().map(CourseRequest::getCourseCode).toList(), a.getCreatedAt()))
                .toList();
    }

    /** Database row + live state of each course-revaluation instance (stage, payment link, marks). */
    @Transactional(readOnly = true)
    public ApplicationDetail detail(String applicationId) {
        RevaluationApplication app = find(applicationId);
        String parentState = null;
        Map<String, CourseView> live = new HashMap<>();
        if (app.getProcessInstanceKey() != null) {
            ProcessInstance parent = query.processInstance(app.getProcessInstanceKey());
            parentState = parent == null ? "STARTING" : parent.getState().name();
            for (ProcessInstance child : query.courseInstances(app.getProcessInstanceKey())) {
                CourseView view = liveCourse(child);
                if (view.courseCode() != null) {
                    live.put(view.courseCode(), view);
                }
            }
        }
        List<CourseView> courses = new ArrayList<>();
        for (CourseRequest c : app.getCourses()) {
            courses.add(live.getOrDefault(c.getCourseCode(), fromDatabase(c)));
        }
        return new ApplicationDetail(app.getApplicationId(), app.getRegisterNo(), app.getStudentName(), app.getStudentEmail(),
                app.getStatus().name(), app.getProcessInstanceKey(), app.getTotalRefund(), app.getCreatedAt(), parentState, courses);
    }

    /** Engine history (parent + every course instance) merged with our own ProcessEvent rows, oldest first. */
    @Transactional(readOnly = true)
    public List<TimelineEntry> timeline(String applicationId) {
        RevaluationApplication app = find(applicationId);
        List<TimelineEntry> entries = new ArrayList<>();
        List<String> requestIds = new ArrayList<>(List.of("-"));
        if (app.getProcessInstanceKey() != null) {
            query.history(app.getProcessInstanceKey()).forEach(e -> entries.add(new TimelineEntry(
                    e.getStartDate(), "CAMUNDA", null, e.getElementName(), e.getState().name())));
            for (ProcessInstance child : query.courseInstances(app.getProcessInstanceKey())) {
                Map<String, JsonNode> v = query.variables(child.getProcessInstanceKey());
                String courseCode = text(v.get("courseCode"));
                String requestId = text(v.get("application"), "requestId");
                if (requestId != null) {
                    requestIds.add(requestId);
                }
                query.history(child.getProcessInstanceKey()).forEach(e -> entries.add(new TimelineEntry(
                        e.getStartDate(), "CAMUNDA", courseCode, e.getElementName(), e.getState().name())));
            }
        }
        events.findByApplicationIdOrRequestIdInOrderByTimestampAsc(applicationId, requestIds).forEach(ev -> entries.add(new TimelineEntry(
                ev.getTimestamp().atOffset(ZoneOffset.UTC), ev.getEventType(), null, ev.getMessage(), null)));
        entries.removeIf(e -> e.time() == null || e.label() == null);
        entries.sort(Comparator.comparing(TimelineEntry::time));
        return entries;
    }

    /** Called by the save-results job worker when the multi-instance has finished. */
    @Transactional
    public void saveResults(SaveResultsInput in) {
        RevaluationApplication app = find(in.applicationId());
        for (CourseResult r : in.courseResults()) {
            if (r == null) {
                continue; // a null element would mean a child ended without courseResult; all paths set it
            }
            app.getCourses().stream().filter(c -> c.getCourseCode().equals(r.courseCode())).findFirst().ifPresent(c -> {
                c.setRequestId(r.requestId());
                c.setStatus(r.status());
                c.setReason(r.reason());
                c.setOriginalMarks(r.originalMarks());
                c.setFinalMarks(r.finalMarks());
                c.setGrade(r.grade());
                c.setRefundAmount(r.refundAmount());
                c.setLetterUrl(r.letterUrl());
            });
        }
        app.setTotalRefund(in.summary() == null ? BigDecimal.ZERO : in.summary().totalRefund());
        app.setStatus(Status.COMPLETED);
        eventLog.record(app.getApplicationId(), null, "WORKER",
                "Results saved: " + (in.summary() == null ? "" : in.summary().changed() + " changed, "
                        + in.summary().unchanged() + " unchanged, " + in.summary().rejected() + " rejected, "
                        + in.summary().cancelled() + " cancelled"));
    }

    private RevaluationApplication find(String applicationId) {
        return applications.findByApplicationId(applicationId)
                .orElseThrow(() -> ApiException.notFound("No application " + applicationId));
    }

    private CourseView liveCourse(ProcessInstance child) {
        long key = child.getProcessInstanceKey();
        Map<String, JsonNode> v = query.variables(key);
        JsonNode result = v.get("courseResult");
        boolean finished = child.getState().name().equals("COMPLETED") || child.getState().name().equals("TERMINATED");
        List<String> active = finished ? List.of() : query.activeElementIds(key);

        String stage = finished ? "Completed" : STAGES.entrySet().stream()
                .filter(s -> active.contains(s.getKey())).map(Map.Entry::getValue).findFirst().orElse("Working");
        String status = result != null && !result.isNull() ? text(result, "status") : "IN_PROGRESS";
        boolean paymentPending = active.contains("Catch_FeePaid");
        // After a third valuation both examiners are named, e.g. "Dr. B. Priya (third valuation: Dr. A. Kumar)"
        String examiner = text(v.get("examiner"), "name");
        String thirdExaminer = text(v.get("thirdExaminer"), "name");
        if (examiner != null && thirdExaminer != null) {
            examiner += " (third valuation: " + thirdExaminer + ")";
        }

        return new CourseView(
                text(v.get("courseCode")), text(v.get("courseName")), text(v.get("application"), "requestId"), key,
                stage, status, Boolean.TRUE.equals(child.getHasIncident()), active.contains("Task_Escalate"),
                text(v.get("paymentUrl")), paymentPending, examiner,
                integer(v.get("originalMarks")),
                result == null ? null : integer(result.get("finalMarks")),
                result == null ? null : text(result, "grade"),
                result == null || result.get("refundAmount") == null || result.get("refundAmount").isNull()
                        ? null : result.get("refundAmount").decimalValue(),
                result == null ? null : text(result, "reason"),
                result == null ? null : text(result, "letterUrl"),
                active);
    }

    private CourseView fromDatabase(CourseRequest c) {
        return new CourseView(c.getCourseCode(), null, c.getRequestId(), null,
                "COMPLETED".equals(c.getApplication().getStatus().name()) ? "Completed" : "Starting",
                c.getStatus(), false, false, null, false, c.getExaminerName(), c.getOriginalMarks(), c.getFinalMarks(),
                c.getGrade(), c.getRefundAmount(), c.getReason(), c.getLetterUrl(), List.of());
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private static String text(JsonNode node, String field) {
        return node == null ? null : text(node.get(field));
    }

    private static Integer integer(JsonNode node) {
        return node == null || node.isNull() || !node.isNumber() ? null : node.asInt();
    }
}
