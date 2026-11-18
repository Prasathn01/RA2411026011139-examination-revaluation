package in.srmist.revaluation.repository;

import in.srmist.revaluation.entity.ProcessEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessEventRepository extends JpaRepository<ProcessEvent, Long> {
    /** Events of one application: written with its applicationId, or with the requestId of one of its courses. */
    List<ProcessEvent> findByApplicationIdOrRequestIdInOrderByTimestampAsc(String applicationId, List<String> requestIds);
}
