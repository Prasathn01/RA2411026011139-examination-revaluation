package in.srmist.revaluation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** The "revaluation:" block of application.yml, read once at startup. */
@ConfigurationProperties(prefix = "revaluation")
public record RevaluationProperties(
        String mockServicesUrl,
        String paymentApiUrl,
        String razorpayKeyId,
        String razorpayKeySecret,
        String hodEmail,
        String coeEmail,
        String coePassword,
        String mailFrom,
        String frontendOrigin) {
}
