package in.srmist.revaluation.service;

import in.srmist.revaluation.entity.CourseRequest;
import in.srmist.revaluation.entity.Examiner;
import in.srmist.revaluation.exception.ApiException;
import in.srmist.revaluation.repository.CourseRequestRepository;
import in.srmist.revaluation.repository.ExaminerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Examiner directory and the assignment rules the Controller of Examinations (CoE) must follow.
 * The CoE chooses; this class checks the choice and records it.
 */
@Service
public class ExaminerService {

    public static final String REVALUATION = "REVALUATION";
    public static final String THIRD_VALUATION = "THIRD_VALUATION";

    private final ExaminerRepository examiners;
    private final CourseRequestRepository courses;
    private final EventLog eventLog;

    public ExaminerService(ExaminerRepository examiners, CourseRequestRepository courses, EventLog eventLog) {
        this.examiners = examiners;
        this.courses = courses;
        this.eventLog = eventLog;
    }

    public List<Examiner> all() {
        return examiners.findAll();
    }

    /**
     * The examiners the CoE may pick, least busy first: available, allowed to value the course,
     * and not in "excluded". For the revaluation, excluded = the first examiner; for the third
     * valuation, excluded = the first examiner and the revaluer.
     */
    public List<Examiner> eligible(String courseCode, Collection<String> excluded) {
        return examiners.findByAvailableTrueOrderByActiveAssignmentsAsc().stream()
                .filter(e -> e.canValue(courseCode))
                .filter(e -> !excluded.contains(e.getExaminerId()))
                .toList();
    }

    /** 400 unless examinerId is one of eligible(courseCode, excluded). Called before the CoE task is completed. */
    public void checkChoice(String examinerId, String courseCode, Collection<String> excluded) {
        if (excluded.contains(examinerId)) {
            throw ApiException.badRequest(examinerId + " has already valued this script. Pick a different examiner.");
        }
        if (eligible(courseCode, excluded).stream().noneMatch(e -> e.getExaminerId().equals(examinerId))) {
            throw ApiException.badRequest(examinerId + " is not available to value " + courseCode + ".");
        }
    }

    /**
     * Adds an external examiner to the directory for one course. Used by the CoE when nobody in
     * the directory may value the course (for example 21CSE735P, whose only examiner did the first valuation).
     */
    @Transactional
    public Examiner addExternal(String name, String email, String courseCode) {
        long n = examiners.count() + 101;
        while (examiners.existsByExaminerId("EXM-" + n)) {
            n++;
        }
        return examiners.save(new Examiner("EXM-" + n, name, email, "External examiner", courseCode, 0));
    }

    /**
     * Records the CoE's choice: counts the workload and stores the examiner on the course row.
     * Idempotent: if the job is retried after the database commit, the same examiner is not counted twice.
     */
    @Transactional
    public Examiner recordAssignment(String applicationId, String courseCode, String requestId, String examinerId, String round) {
        Examiner examiner = examiners.findByExaminerId(examinerId)
                .orElseThrow(() -> new IllegalStateException("Examiner " + examinerId + " is not in the directory"));
        CourseRequest course = courses.findByApplication_ApplicationIdAndCourseCode(applicationId, courseCode).orElse(null);
        boolean third = THIRD_VALUATION.equals(round);
        String already = course == null ? null : third ? course.getThirdExaminerId() : course.getExaminerId();
        if (Objects.equals(already, examinerId)) {
            return examiner;
        }

        examiner.setActiveAssignments(examiner.getActiveAssignments() + 1);
        if (course != null) {
            course.setRequestId(requestId);
            if (third) {
                course.setThirdExaminerId(examinerId);
                course.setThirdExaminerName(examiner.getName());
            } else {
                course.setExaminerId(examinerId);
                course.setExaminerName(examiner.getName());
                course.setStatus("UNDER_REVALUATION");
            }
        }
        eventLog.record(applicationId, requestId, "COE",
                courseCode + ": CoE assigned " + examiner.getName() + " (" + examinerId + ") for the " + (third ? "third valuation" : "revaluation"));
        return examiner;
    }
}
