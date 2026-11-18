package in.srmist.revaluation.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * One course inside an application. Each row matches one instance of the parallel
 * multi-instance "Revaluate course" (one course-revaluation child process).
 */
@Entity
@Table(name = "course_request",
        uniqueConstraints = @UniqueConstraint(columnNames = {"application_id", "courseCode"}))
public class CourseRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "application_id")
    private RevaluationApplication application;

    @Column(nullable = false, length = 12)
    private String courseCode;

    /** REV-registerNo-courseCode-suffix, created by the "Register course request" script task. */
    @Column(length = 40)
    private String requestId;

    /** SUBMITTED, UNDER_REVALUATION, then the final courseResult.status: CHANGED / UNCHANGED / REJECTED / CANCELLED. */
    @Column(nullable = false, length = 20)
    private String status = "SUBMITTED";

    private Integer originalMarks;
    private Integer finalMarks;
    @Column(length = 4)
    private String grade;
    private BigDecimal refundAmount;
    @Column(length = 20)
    private String examinerId;
    @Column(length = 80)
    private String examinerName;
    /** Set only when a third valuation was needed; the CoE must pick someone new. */
    @Column(length = 20)
    private String thirdExaminerId;
    @Column(length = 80)
    private String thirdExaminerName;
    @Column(length = 300)
    private String reason;
    @Column(length = 300)
    private String letterUrl;

    public Long getId() { return id; }
    public RevaluationApplication getApplication() { return application; }
    public void setApplication(RevaluationApplication application) { this.application = application; }
    public String getCourseCode() { return courseCode; }
    public void setCourseCode(String courseCode) { this.courseCode = courseCode; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getOriginalMarks() { return originalMarks; }
    public void setOriginalMarks(Integer originalMarks) { this.originalMarks = originalMarks; }
    public Integer getFinalMarks() { return finalMarks; }
    public void setFinalMarks(Integer finalMarks) { this.finalMarks = finalMarks; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }
    public String getExaminerId() { return examinerId; }
    public void setExaminerId(String examinerId) { this.examinerId = examinerId; }
    public String getExaminerName() { return examinerName; }
    public void setExaminerName(String examinerName) { this.examinerName = examinerName; }
    public String getThirdExaminerId() { return thirdExaminerId; }
    public void setThirdExaminerId(String thirdExaminerId) { this.thirdExaminerId = thirdExaminerId; }
    public String getThirdExaminerName() { return thirdExaminerName; }
    public void setThirdExaminerName(String thirdExaminerName) { this.thirdExaminerName = thirdExaminerName; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getLetterUrl() { return letterUrl; }
    public void setLetterUrl(String letterUrl) { this.letterUrl = letterUrl; }
}
