package in.srmist.revaluation;

import io.camunda.client.annotation.Deployment;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point of the backend.
 *
 * On startup the three BPMN processes, the DMN decisions and the forms (copied from
 * ../bpmn-models into the jar by Maven) are deployed to Camunda, so a fresh
 * "docker compose up" or "mvn spring-boot:run" needs no manual deployment step.
 * Camunda only creates a new version when a file has actually changed.
 *
 * @EnableScheduling switches on the payment bridge, which polls the payment API every 5 seconds.
 */
@SpringBootApplication
@EnableScheduling
@Deployment(resources = {
        "classpath*:/models/*.dmn",
        "classpath*:/models/forms/*.form",
        "classpath*:/models/*.bpmn"
})
public class RevaluationBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(RevaluationBackendApplication.class, args);
    }
}
