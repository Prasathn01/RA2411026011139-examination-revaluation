package in.srmist.revaluation.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** One application from a student. It can contain several courses (see CourseRequest). */
@Entity
@Table(name = "revaluation_application")
public class RevaluationApplication {

    /** Lifecycle of the whole application, as stored in our own database. */
    public enum Status { SUBMITTED, IN_PROGRESS, COMPLETED, FAILED_TO_START }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Business key shown to the student, e.g. APP-261118094512345. Also a process variable. */
    @Column(nullable = false, unique = true, length = 40)
    private String applicationId;

    @Column(nullable = false, length = 15)
    private String registerNo;

    @Column(nullable = false, length = 80)
    private String studentName;

    @Column(nullable = false, length = 120)
    private String studentEmail;

    /** Key of the revaluation-application process instance in Camunda. */
    private Long processInstanceKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.SUBMITTED;

    private BigDecimal totalRefund;

    private Instant createdAt;
    private Instant updatedAt;

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id")
    private List<CourseRequest> courses = new ArrayList<>();

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void addCourse(CourseRequest course) {
        course.setApplication(this);
        courses.add(course);
    }

    public Long getId() { return id; }
    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String applicationId) { this.applicationId = applicationId; }
    public String getRegisterNo() { return registerNo; }
    public void setRegisterNo(String registerNo) { this.registerNo = registerNo; }
    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }
    public String getStudentEmail() { return studentEmail; }
    public void setStudentEmail(String studentEmail) { this.studentEmail = studentEmail; }
    public Long getProcessInstanceKey() { return processInstanceKey; }
    public void setProcessInstanceKey(Long processInstanceKey) { this.processInstanceKey = processInstanceKey; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public BigDecimal getTotalRefund() { return totalRefund; }
    public void setTotalRefund(BigDecimal totalRefund) { this.totalRefund = totalRefund; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<CourseRequest> getCourses() { return courses; }
}
