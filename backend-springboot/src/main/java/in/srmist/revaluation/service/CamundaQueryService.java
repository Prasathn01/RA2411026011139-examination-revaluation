package in.srmist.revaluation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.enums.ElementInstanceType;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.Variable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only questions to Camunda, used by the live tracker and the task inbox.
 *
 * These calls go to the Orchestration Cluster REST API search endpoints (v2). The data there is
 * filled from the engine a moment after things happen, so the tracker can lag by about a second.
 */
@Service
public class CamundaQueryService {

    /** Element types worth showing on the timeline (gateways and flow internals are left out). */
    private static final Set<ElementInstanceType> TIMELINE_TYPES = Set.of(
            ElementInstanceType.START_EVENT, ElementInstanceType.END_EVENT, ElementInstanceType.BOUNDARY_EVENT,
            ElementInstanceType.INTERMEDIATE_CATCH_EVENT, ElementInstanceType.SERVICE_TASK, ElementInstanceType.USER_TASK,
            ElementInstanceType.SCRIPT_TASK, ElementInstanceType.BUSINESS_RULE_TASK, ElementInstanceType.CALL_ACTIVITY,
            ElementInstanceType.SUB_PROCESS);

    private final CamundaClient camunda;
    private final ObjectMapper json;

    public CamundaQueryService(CamundaClient camunda, ObjectMapper json) {
        this.camunda = camunda;
        this.json = json;
    }

    public ProcessInstance processInstance(long key) {
        List<ProcessInstance> items = camunda.newProcessInstanceSearchRequest()
                .filter(f -> f.processInstanceKey(key))
                .send().join().items();
        return items.isEmpty() ? null : items.get(0);
    }

    /** The course-revaluation instances started by the parent's parallel multi-instance. */
    public List<ProcessInstance> courseInstances(long parentKey) {
        return camunda.newProcessInstanceSearchRequest()
                .filter(f -> f.parentProcessInstanceKey(parentKey).processDefinitionId("course-revaluation"))
                .page(p -> p.limit(50))
                .send().join().items();
    }

    /**
     * All variables of one process instance, parsed from JSON.
     * If the same name exists in several scopes, the process-level value wins.
     */
    public Map<String, JsonNode> variables(long processInstanceKey) {
        List<Variable> items = camunda.newVariableSearchRequest()
                .withFullValues()
                .filter(f -> f.processInstanceKey(processInstanceKey))
                .page(p -> p.limit(300))
                .send().join().items();
        Map<String, JsonNode> result = new HashMap<>();
        for (Variable v : items) {
            boolean processScope = v.getScopeKey() != null && v.getScopeKey() == processInstanceKey;
            if (processScope || !result.containsKey(v.getName())) {
                result.put(v.getName(), parse(v.getValue()));
            }
        }
        return result;
    }

    /** IDs of the elements a process instance is currently waiting in (e.g. Task_Revalue). */
    public List<String> activeElementIds(long processInstanceKey) {
        return camunda.newElementInstanceSearchRequest()
                .filter(f -> f.processInstanceKey(processInstanceKey).state(ElementInstanceState.ACTIVE))
                .page(p -> p.limit(50))
                .send().join().items().stream()
                .map(ElementInstance::getElementId)
                .toList();
    }

    /** Element history of one instance for the timeline, oldest first. */
    public List<ElementInstance> history(long processInstanceKey) {
        return camunda.newElementInstanceSearchRequest()
                .filter(f -> f.processInstanceKey(processInstanceKey))
                .sort(s -> s.startDate().asc())
                .page(p -> p.limit(200))
                .send().join().items().stream()
                .filter(e -> TIMELINE_TYPES.contains(e.getType()))
                .toList();
    }

    private JsonNode parse(String value) {
        try {
            return json.readTree(value);
        } catch (Exception e) {
            return json.getNodeFactory().textNode(value);
        }
    }
}
