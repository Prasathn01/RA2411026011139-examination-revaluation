package in.srmist.revaluation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.srmist.revaluation.dto.TaskRequests;
import in.srmist.revaluation.dto.Views.ExaminerOption;
import in.srmist.revaluation.dto.Views.TaskDetail;
import in.srmist.revaluation.dto.Views.TaskView;
import in.srmist.revaluation.exception.ApiException;
import in.srmist.revaluation.service.AuthService.StaffSession;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.UserTask;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The staff task inbox. React never talks to Camunda; it calls these methods through
 * TaskController, and this class uses the Camunda user-task API (search and complete).
 *
 * Who sees what:
 *  - the CoE sees the tasks of candidate group "coe" (assign examiner, assign third examiner,
 *    escalated delays, manual result updates) and can complete them directly;
 *  - an examiner sees only the tasks whose assignee is their username. The BPMN sets the assignee
 *    (=examiner.username / =thirdExaminer.username), so there is nothing to claim.
 */
@Service
public class TaskService {

    static final String COE_GROUP = "coe";

    /**
     * Which variables each task's form may see. This is where the BLIND rule is enforced:
     * the revalue and third-valuation tasks never receive originalMarks (or the first revaluation),
     * so the examiner cannot be biased by them, even by looking at the network response.
     */
    private static final Map<String, List<String>> VISIBLE = Map.of(
            "Task_CoeAssign", List.of("courseCode", "courseName", "requestId", "registerNo", "originalExaminerId"),
            "Task_CoeAssignThird", List.of("courseCode", "courseName", "requestId", "registerNo", "originalExaminerId", "examinerName"),
            "Task_Revalue", List.of("courseCode", "courseName", "requestId", "examinerName"),
            "Task_ThirdValuation", List.of("courseCode", "courseName", "requestId"),
            "Task_ManualUpdate", List.of("courseCode", "courseName", "requestId", "registerNo", "finalMarks", "grade"),
            "Task_Escalate", List.of("courseCode", "courseName", "requestId", "examinerName"));

    private final CamundaClient camunda;
    private final CamundaQueryService query;
    private final ExaminerService examiners;
    private final EventLog eventLog;
    private final ObjectMapper json;

    public TaskService(CamundaClient camunda, CamundaQueryService query, ExaminerService examiners,
                       EventLog eventLog, ObjectMapper json) {
        this.camunda = camunda;
        this.query = query;
        this.examiners = examiners;
        this.eventLog = eventLog;
        this.json = json;
    }

    /** Open tasks of the signed-in user: the "coe" group for the CoE, own assignments for an examiner. */
    public List<TaskView> list(StaffSession user) {
        List<UserTask> open = camunda.newUserTaskSearchRequest()
                .filter(f -> {
                    f.state(UserTaskState.CREATED);
                    if (user.isCoe()) f.candidateGroup(COE_GROUP);
                    else f.assignee(user.username());
                })
                .sort(s -> s.creationDate().asc())
                .page(p -> p.limit(100))
                .send().join().items();

        // A revalue task counts as "escalated" while an "Escalate delay to CoE" task is open in the same instance.
        Set<Long> escalated = camunda.newUserTaskSearchRequest()
                .filter(f -> f.state(UserTaskState.CREATED).elementId("Task_Escalate"))
                .send().join().items().stream()
                .map(UserTask::getProcessInstanceKey).collect(Collectors.toSet());

        return open.stream().map(t -> view(t, escalated.contains(t.getProcessInstanceKey()))).toList();
    }

    public TaskDetail get(long taskKey, StaffSession user) {
        UserTask task = task(taskKey, user);
        Map<String, JsonNode> all = query.variables(task.getProcessInstanceKey());
        Map<String, Object> visible = new LinkedHashMap<>();
        for (String name : VISIBLE.getOrDefault(task.getElementId(), List.of())) {
            JsonNode value = switch (name) {
                case "requestId" -> path(all.get("application"), "requestId");
                case "examinerName" -> path(all.get("examiner"), "name");
                case "finalMarks" -> path(all.get("decision"), "finalMarks");
                case "grade" -> path(all.get("decision"), "grade");
                default -> all.get(name);
            };
            visible.put(name, value == null ? null : json.convertValue(value, Object.class));
        }
        if (isAssignTask(task)) {
            // The drop-down of examiners the CoE may pick (least busy first)
            visible.put("examinerOptions", examiners.eligible(text(all.get("courseCode")), excluded(task, all)).stream()
                    .map(e -> new ExaminerOption(e.getExaminerId(), e.getName(), e.getDepartment(), e.getActiveAssignments()))
                    .toList());
        }
        return new TaskDetail(view(task, false), visible);
    }

    /** Checks the fields this particular task needs, then completes it with exactly those variables. */
    public void complete(long taskKey, TaskRequests.Complete body, StaffSession user) {
        UserTask task = task(taskKey, user);
        Map<String, Object> vars = new HashMap<>();
        switch (task.getElementId()) {
            case "Task_CoeAssign", "Task_CoeAssignThird" -> vars.put("selectedExaminerId", chooseExaminer(task, body));
            case "Task_Revalue" -> {
                require(body.revaluedMarks() != null, "Enter the revalued marks (0-100).");
                vars.put("revaluedMarks", body.revaluedMarks());
                vars.put("examinerRemarks", body.examinerRemarks() == null ? "" : body.examinerRemarks());
            }
            case "Task_ThirdValuation" -> {
                require(body.thirdValuationMarks() != null, "Enter the third valuation marks (0-100).");
                vars.put("thirdValuationMarks", body.thirdValuationMarks());
                vars.put("thirdValuationRemarks", body.thirdValuationRemarks() == null ? "" : body.thirdValuationRemarks());
            }
            case "Task_ManualUpdate", "Task_Escalate" -> {
                require(body.actionTaken() != null && !body.actionTaken().isBlank(), "Describe the action taken.");
                require(Boolean.TRUE.equals(body.actionCompleted()), "Tick 'Done' to confirm the action is complete.");
                vars.put("actionTaken", body.actionTaken().trim());
                vars.put("actionCompleted", true);
            }
            default -> throw ApiException.badRequest("This portal cannot complete task " + task.getElementId());
        }
        camunda.newCompleteUserTaskCommand(taskKey).variables(vars).send().join();
        eventLog.record(null, requestId(task), "TASK", task.getName() + " completed by " + user.displayName());
    }

    /**
     * The CoE either picks an examiner from the list (checked against the rules again here, so a
     * hand-made request cannot break them) or adds an external examiner by name and email.
     */
    private String chooseExaminer(UserTask task, TaskRequests.Complete body) {
        Map<String, JsonNode> all = query.variables(task.getProcessInstanceKey());
        String courseCode = text(all.get("courseCode"));
        if (body.selectedExaminerId() != null && !body.selectedExaminerId().isBlank()) {
            String id = body.selectedExaminerId().trim();
            examiners.checkChoice(id, courseCode, excluded(task, all));
            return id;
        }
        require(body.examinerName() != null && !body.examinerName().isBlank()
                        && body.examinerEmail() != null && !body.examinerEmail().isBlank(),
                "Pick an examiner, or enter the name and email of an external examiner.");
        return examiners.addExternal(body.examinerName().trim(), body.examinerEmail().trim(), courseCode).getExaminerId();
    }

    /** First examiner always; for the third valuation also the revaluer. */
    private static Set<String> excluded(UserTask task, Map<String, JsonNode> all) {
        Set<String> out = new LinkedHashSet<>();
        String original = text(all.get("originalExaminerId"));
        if (original != null) out.add(original);
        if ("Task_CoeAssignThird".equals(task.getElementId())) {
            String revaluer = text(path(all.get("examiner"), "id"));
            if (revaluer != null) out.add(revaluer);
        }
        return out;
    }

    private static boolean isAssignTask(UserTask task) {
        return task.getElementId().equals("Task_CoeAssign") || task.getElementId().equals("Task_CoeAssignThird");
    }

    /** Loads an open task and checks the signed-in user may work on it (403 otherwise). */
    private UserTask task(long taskKey, StaffSession user) {
        List<UserTask> items = camunda.newUserTaskSearchRequest()
                .filter(f -> f.userTaskKey(taskKey))
                .send().join().items();
        if (items.isEmpty()) {
            throw ApiException.notFound("Task " + taskKey + " not found");
        }
        UserTask task = items.get(0);
        if (task.getState() != UserTaskState.CREATED) {
            throw ApiException.badRequest("Task " + taskKey + " is already " + task.getState().name().toLowerCase());
        }
        boolean allowed = user.isCoe()
                ? task.getCandidateGroups() != null && task.getCandidateGroups().contains(COE_GROUP)
                : user.username().equals(task.getAssignee());
        if (!allowed) {
            throw ApiException.forbidden("This task is not assigned to you.");
        }
        return task;
    }

    private TaskView view(UserTask t, boolean escalated) {
        Map<String, JsonNode> v = query.variables(t.getProcessInstanceKey());
        return new TaskView(t.getUserTaskKey(), t.getName(), t.getElementId(),
                text(v.get("courseCode")), text(path(v.get("application"), "requestId")),
                t.getCandidateGroups(), t.getAssignee(), t.getCreationDate(), escalated);
    }

    private String requestId(UserTask task) {
        return text(path(query.variables(task.getProcessInstanceKey()).get("application"), "requestId"));
    }

    private static JsonNode path(JsonNode node, String field) {
        return node == null || node.isNull() ? null : node.get(field);
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw ApiException.badRequest(message);
        }
    }
}
