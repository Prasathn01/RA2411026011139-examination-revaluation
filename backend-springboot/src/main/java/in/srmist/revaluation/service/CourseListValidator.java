package in.srmist.revaluation.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Checks the course list before a process is started.
 * The same rule exists in the BPMN ("Check course list" script task, FEEL
 * count(courses) = count(distinct values(courses))), so a client that bypasses this API still
 * cannot start an application with duplicates. Checking here first gives the student an
 * immediate, clear error instead of a rejected process.
 */
public final class CourseListValidator {

    private CourseListValidator() { }

    /** Returns null when the list is fine, otherwise the error message for the student. */
    public static String check(List<String> courses) {
        if (courses == null || courses.isEmpty()) {
            return "Add at least one course.";
        }
        Set<String> seen = new HashSet<>();
        for (String code : courses) {
            if (!seen.add(code)) {
                return "Course " + code + " is listed twice. Each course can be requested once per application.";
            }
        }
        return null;
    }
}
