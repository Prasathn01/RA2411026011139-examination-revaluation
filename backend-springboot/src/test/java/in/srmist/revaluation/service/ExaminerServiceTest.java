package in.srmist.revaluation.service;

import in.srmist.revaluation.entity.CourseRequest;
import in.srmist.revaluation.entity.Examiner;
import in.srmist.revaluation.exception.ApiException;
import in.srmist.revaluation.repository.CourseRequestRepository;
import in.srmist.revaluation.repository.ExaminerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The rules the CoE's examiner choice must follow, and how an assignment is recorded.
 * Repositories are replaced by Mockito mocks, so no database or Camunda is needed.
 */
class ExaminerServiceTest {

    private ExaminerRepository examinerRepo;
    private CourseRequestRepository courseRepo;
    private ExaminerService service;

    // The repository returns examiners already sorted by activeAssignments (least busy first).
    private final Examiner kumar = new Examiner("EXM-101", "Dr. A. Kumar", "a@srmist.test", "CSE", "21CSC305P", 0);
    private final Examiner priya = new Examiner("EXM-102", "Dr. B. Priya", "b@srmist.test", "CSE", "21CSC305P,21CSE251T", 1);
    private final Examiner meena = new Examiner("EXM-104", "Dr. D. Meena", "d@srmist.test", "ECE", "21ECE201T", 1);
    private final Examiner ravi = new Examiner("EXM-103", "Dr. C. Ravi", "c@srmist.test", "CSE", "21CSC305P", 2);

    @BeforeEach
    void setUp() {
        examinerRepo = mock(ExaminerRepository.class);
        courseRepo = mock(CourseRequestRepository.class);
        service = new ExaminerService(examinerRepo, courseRepo, mock(EventLog.class));
        when(examinerRepo.findByAvailableTrueOrderByActiveAssignmentsAsc()).thenReturn(List.of(kumar, priya, meena, ravi));
        when(courseRepo.findByApplication_ApplicationIdAndCourseCode(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void eligibleListLeavesOutTheFirstExaminerAndOtherSubjects() {
        // EXM-101 did the first valuation; EXM-104 does not teach 21CSC305P
        assertThat(service.eligible("21CSC305P", Set.of("EXM-101")))
                .extracting(Examiner::getExaminerId).containsExactly("EXM-102", "EXM-103");
    }

    @Test
    void thirdValuationAlsoLeavesOutTheRevaluer() {
        assertThat(service.eligible("21CSC305P", Set.of("EXM-101", "EXM-102")))
                .extracting(Examiner::getExaminerId).containsExactly("EXM-103");
    }

    @Test
    void choiceOfTheRevaluerForTheThirdValuationIsRejected() {
        assertThatThrownBy(() -> service.checkChoice("EXM-102", "21CSC305P", Set.of("EXM-101", "EXM-102")))
                .isInstanceOf(ApiException.class).hasMessageContaining("already valued");
        assertThatThrownBy(() -> service.checkChoice("EXM-104", "21CSC305P", Set.of("EXM-101")))
                .isInstanceOf(ApiException.class).hasMessageContaining("not available");
    }

    @Test
    void recordingAnAssignmentCountsWorkloadOnceEvenWhenRetried() {
        CourseRequest course = new CourseRequest();
        when(courseRepo.findByApplication_ApplicationIdAndCourseCode("APP-1", "21CSC305P")).thenReturn(Optional.of(course));
        when(examinerRepo.findByExaminerId("EXM-102")).thenReturn(Optional.of(priya));

        service.recordAssignment("APP-1", "21CSC305P", "REV-1", "EXM-102", ExaminerService.REVALUATION);
        service.recordAssignment("APP-1", "21CSC305P", "REV-1", "EXM-102", ExaminerService.REVALUATION);   // retried job

        assertThat(priya.getActiveAssignments()).isEqualTo(2);   // 1 before, counted once
        assertThat(course.getExaminerId()).isEqualTo("EXM-102");
        assertThat(course.getStatus()).isEqualTo("UNDER_REVALUATION");
    }

    @Test
    void thirdValuationIsStoredSeparatelyFromTheRevaluer() {
        CourseRequest course = new CourseRequest();
        course.setExaminerId("EXM-102");
        when(courseRepo.findByApplication_ApplicationIdAndCourseCode("APP-1", "21CSC305P")).thenReturn(Optional.of(course));
        when(examinerRepo.findByExaminerId("EXM-103")).thenReturn(Optional.of(ravi));

        service.recordAssignment("APP-1", "21CSC305P", "REV-1", "EXM-103", ExaminerService.THIRD_VALUATION);

        assertThat(course.getExaminerId()).isEqualTo("EXM-102");
        assertThat(course.getThirdExaminerId()).isEqualTo("EXM-103");
        assertThat(ravi.getActiveAssignments()).isEqualTo(3);
    }
}
