package in.srmist.revaluation.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The course list rule checked before a process instance is started. */
class CourseListValidatorTest {

    @Test
    void acceptsDistinctCourses() {
        assertThat(CourseListValidator.check(List.of("21CSC305P", "21CSE251T"))).isNull();
    }

    @Test
    void rejectsEmptyList() {
        assertThat(CourseListValidator.check(List.of())).isEqualTo("Add at least one course.");
        assertThat(CourseListValidator.check(null)).isEqualTo("Add at least one course.");
    }

    @Test
    void rejectsDuplicateCourseAndNamesIt() {
        assertThat(CourseListValidator.check(List.of("21CSC305P", "21CSE251T", "21CSC305P")))
                .contains("21CSC305P").contains("listed twice");
    }
}
