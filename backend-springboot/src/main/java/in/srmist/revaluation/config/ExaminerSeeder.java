package in.srmist.revaluation.config;

import in.srmist.revaluation.entity.Examiner;
import in.srmist.revaluation.repository.ExaminerRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fills the examiner table on the first start (same five examiners as the Review 2 mock).
 *
 * A runner is used instead of data.sql because it works unchanged on H2 and PostgreSQL and
 * does not insert duplicates when the database already has the rows.
 *
 * 21CSE735P has only one examiner, EXM-105, who also did the first valuation. That is how the
 * "no examiner available" scenario happens: the CoE's list is empty, so the CoE adds an external examiner.
 */
@Component
public class ExaminerSeeder implements CommandLineRunner {

    private final ExaminerRepository examiners;

    public ExaminerSeeder(ExaminerRepository examiners) {
        this.examiners = examiners;
    }

    @Override
    public void run(String... args) {
        if (examiners.count() > 0) {
            return;
        }
        examiners.saveAll(List.of(
                new Examiner("EXM-101", "Dr. A. Kumar", "a.kumar@examiners.test", "Computing Technologies", "21CSC305P,21CSC303J,21MAB201T", 2),
                new Examiner("EXM-102", "Dr. B. Priya", "b.priya@examiners.test", "Computational Intelligence", "21CSC305P,21CSC303J,21CSC306L,21CSE251T", 0),
                new Examiner("EXM-103", "Dr. C. Ravi", "c.ravi@examiners.test", "Mathematics", "21CSC305P,21MAB201T,21CSE251T", 1),
                new Examiner("EXM-104", "Dr. D. Meena", "d.meena@examiners.test", "Computing Technologies", "21CSC303J,21MAB201T", 0),
                new Examiner("EXM-105", "Dr. E. Suresh", "e.suresh@examiners.test", "Computational Intelligence", "21CSE735P", 0)));
    }
}
