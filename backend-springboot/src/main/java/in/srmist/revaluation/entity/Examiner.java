package in.srmist.revaluation.entity;

import jakarta.persistence.*;
import java.util.Arrays;

/** Examiner directory. Replaces the hard-coded examiner list of Review 2 (seeded by ExaminerSeeder). */
@Entity
@Table(name = "examiner")
public class Examiner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String examinerId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 120)
    private String email;

    @Column(length = 80)
    private String department;

    /** Course codes this examiner may value, comma separated, e.g. "21CSC305P,21MAB201T". */
    @Column(nullable = false, length = 200)
    private String courses;

    /** Number of open revaluations; the least busy examiner is picked first. */
    private int activeAssignments;

    private boolean available = true;

    protected Examiner() { }

    public Examiner(String examinerId, String name, String email, String department, String courses, int activeAssignments) {
        this.examinerId = examinerId;
        this.name = name;
        this.email = email;
        this.department = department;
        this.courses = courses;
        this.activeAssignments = activeAssignments;
    }

    public boolean canValue(String courseCode) {
        return Arrays.asList(courses.split(",")).contains(courseCode);
    }

    public Long getId() { return id; }
    public String getExaminerId() { return examinerId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getDepartment() { return department; }
    public String getCourses() { return courses; }
    public int getActiveAssignments() { return activeAssignments; }
    public void setActiveAssignments(int activeAssignments) { this.activeAssignments = activeAssignments; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
}
