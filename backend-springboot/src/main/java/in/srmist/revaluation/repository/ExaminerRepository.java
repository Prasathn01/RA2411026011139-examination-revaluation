package in.srmist.revaluation.repository;

import in.srmist.revaluation.entity.Examiner;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExaminerRepository extends JpaRepository<Examiner, Long> {
    Optional<Examiner> findByExaminerId(String examinerId);
    List<Examiner> findByAvailableTrueOrderByActiveAssignmentsAsc();
    boolean existsByExaminerId(String examinerId);
}
