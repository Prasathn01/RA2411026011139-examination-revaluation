package in.srmist.revaluation.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Audit trail written by the backend (application submitted, payment received, examiner
 * assigned, task completed, ...). The live tracker merges these rows with the element
 * history it reads from Camunda.
 */
@Entity
@Table(name = "process_event", indexes = @Index(columnList = "applicationId"))
public class ProcessEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 40)
    private String applicationId;

    @Column(length = 40)
    private String requestId;

    /** Who wrote the event: BACKEND, WORKER, PAYMENT_BRIDGE or TASK. */
    @Column(nullable = false, length = 20)
    private String eventType;

    @Column(nullable = false, length = 400)
    private String message;

    @Column(nullable = false)
    private Instant timestamp;

    protected ProcessEvent() { }

    public ProcessEvent(String applicationId, String requestId, String eventType, String message) {
        this.applicationId = applicationId;
        this.requestId = requestId;
        this.eventType = eventType;
        this.message = message.length() > 400 ? message.substring(0, 400) : message;
        this.timestamp = Instant.now();
    }

    public Long getId() { return id; }
    public String getApplicationId() { return applicationId; }
    public String getRequestId() { return requestId; }
    public String getEventType() { return eventType; }
    public String getMessage() { return message; }
    public Instant getTimestamp() { return timestamp; }
}
