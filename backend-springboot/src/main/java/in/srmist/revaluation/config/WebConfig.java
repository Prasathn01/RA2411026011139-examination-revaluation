package in.srmist.revaluation.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Configuration
@EnableConfigurationProperties(RevaluationProperties.class)
public class WebConfig implements WebMvcConfigurer {

    private final RevaluationProperties props;

    public WebConfig(RevaluationProperties props) {
        this.props = props;
    }

    /**
     * Lets the React dev server (Vite, port 5173) call /api directly if someone opens it without
     * the Vite proxy. In Docker the browser talks to nginx, which proxies /api, so no CORS is needed.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins(props.frontendOrigin()).allowedMethods("GET", "POST");
    }

    /** HTTP client for the mock results system and document store. */
    @Bean
    RestClient mockServicesClient() {
        return RestClient.builder().baseUrl(props.mockServicesUrl()).build();
    }

    /** HTTP client for the Razorpay-shaped payment API, with the same basic-auth keys the connectors use. */
    @Bean
    RestClient paymentApiClient() {
        String token = Base64.getEncoder().encodeToString(
                (props.razorpayKeyId() + ":" + props.razorpayKeySecret()).getBytes(StandardCharsets.UTF_8));
        return RestClient.builder()
                .baseUrl(props.paymentApiUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + token)
                .build();
    }
}
