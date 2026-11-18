package in.srmist.revaluation.repository;

import in.srmist.revaluation.entity.RevaluationApplication;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RevaluationApplicationRepository extends JpaRepository<RevaluationApplication, Long> {
    Optional<RevaluationApplication> findByApplicationId(String applicationId);
    List<RevaluationApplication> findAllByOrderByCreatedAtDesc();
}
