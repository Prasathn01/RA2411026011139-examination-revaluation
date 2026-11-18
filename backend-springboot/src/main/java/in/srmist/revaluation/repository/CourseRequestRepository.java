package in.srmist.revaluation.repository;

import in.srmist.revaluation.entity.CourseRequest;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRequestRepository extends JpaRepository<CourseRequest, Long> {
    Optional<CourseRequest> findByApplication_ApplicationIdAndCourseCode(String applicationId, String courseCode);
}
