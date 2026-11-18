package in.srmist.revaluation.service;

import in.srmist.revaluation.entity.ProcessEvent;
import in.srmist.revaluation.repository.ProcessEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Writes ProcessEvent rows (the audit trail shown in the live tracker) and logs the same line. */
@Service
public class EventLog {

    private static final Logger log = LoggerFactory.getLogger(EventLog.class);

    private final ProcessEventRepository events;

    public EventLog(ProcessEventRepository events) {
        this.events = events;
    }

    public void record(String applicationId, String requestId, String type, String message) {
        log.info("[{}] {} {}: {}", type, applicationId, requestId == null ? "" : requestId, message);
        events.save(new ProcessEvent(applicationId, requestId, type, message));
    }
}
