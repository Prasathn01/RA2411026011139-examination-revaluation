package in.srmist.revaluation.config;

import in.srmist.revaluation.service.AuthService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** Creates the Controller of Examinations login ("coe") on the first start; the password comes from revaluation.coe-password. */
@Component
public class CoeAccountSeeder implements CommandLineRunner {

    private final AuthService auth;
    private final RevaluationProperties props;

    public CoeAccountSeeder(AuthService auth, RevaluationProperties props) {
        this.auth = auth;
        this.props = props;
    }

    @Override
    public void run(String... args) {
        auth.ensureCoeAccount(props.coePassword());
    }
}
