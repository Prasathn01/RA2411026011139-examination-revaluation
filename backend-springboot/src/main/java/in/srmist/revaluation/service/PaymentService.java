package in.srmist.revaluation.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.client.CamundaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Payment bridge + "Pay fee (demo)" helper.
 *
 * Razorpay can call a webhook when a payment link is paid, but a webhook needs a public URL,
 * which a laptop demo does not have. So every 5 seconds this service reads the payment links
 * (the same API the "Create fee payment link" connector used) and, for each newly paid
 * revaluation link, publishes the message "RevaluationFeePaid" to Camunda.
 *
 * Correlation: the message's correlation key is the link's reference_id, which the process set to
 * application.requestId (REV-registerNo-courseCode-suffix). Several course instances can be waiting
 * for this message at the same time; because every course has its own requestId, each message
 * reaches exactly one waiting instance.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final RestClient paymentApi;
    private final RestClient mockServices;
    private final CamundaClient camunda;
    private final EventLog eventLog;
    /** Links already published in this run. Camunda also rejects a repeated messageId, so a restart is safe. */
    private final Set<String> published = ConcurrentHashMap.newKeySet();

    public PaymentService(RestClient paymentApiClient, RestClient mockServicesClient, CamundaClient camunda, EventLog eventLog) {
        this.paymentApi = paymentApiClient;
        this.mockServices = mockServicesClient;
        this.camunda = camunda;
        this.eventLog = eventLog;
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    public void bridge() {
        List<PaymentLink> links;
        try {
            links = paymentApi.get().uri("/payment_links").retrieve().body(PaymentLinks.class).paymentLinks();
        } catch (Exception e) {
            log.debug("Payment API not reachable: {}", e.getMessage());
            return;
        }
        for (PaymentLink link : links) {
            if (!"paid".equals(link.status()) || link.referenceId() == null
                    || !link.referenceId().startsWith("REV-") || published.contains(link.id())) {
                continue;
            }
            String paymentId = link.payments() == null || link.payments().isEmpty() ? null : link.payments().get(0).paymentId();
            try {
                camunda.newPublishMessageCommand()
                        .messageName("RevaluationFeePaid")
                        .correlationKey(link.referenceId())
                        .messageId(link.id())                  // the same payment is never delivered twice
                        .timeToLive(Duration.ofHours(1))      // kept for an hour if no instance is waiting yet
                        .variables(Map.of("paymentId", paymentId == null ? "" : paymentId, "paymentLinkId", link.id(), "amountPaid", link.amountPaid() / 100))
                        .send().join();
                published.add(link.id());
                eventLog.record(null, link.referenceId(), "PAYMENT_BRIDGE",
                        "Fee paid (" + paymentId + "); message RevaluationFeePaid published");
            } catch (Exception e) {
                if (String.valueOf(e.getMessage()).contains("ALREADY_EXISTS")) {
                    published.add(link.id());   // published before a restart
                } else {
                    log.warn("Could not publish payment for {}: {}", link.referenceId(), e.getMessage());
                }
            }
        }
    }

    /**
     * Demo helper behind the "Pay fee (demo)" button: does what the student would do on the
     * test checkout page. The bridge above then notices the payment within 5 seconds.
     */
    public String simulate(String requestId) {
        PaymentLink link = paymentApi.get().uri("/payment_links").retrieve().body(PaymentLinks.class).paymentLinks().stream()
                .filter(l -> requestId.equals(l.referenceId()))
                .findFirst()
                .orElseThrow(() -> in.srmist.revaluation.exception.ApiException.notFound("No payment link for " + requestId + " yet"));
        if ("paid".equals(link.status())) {
            return "Already paid";
        }
        mockServices.post().uri("/pay/{id}", link.id())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("method=upi")
                .retrieve().toBodilessEntity();
        return "Payment made on the test checkout. The process continues within about 5 seconds.";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PaymentLinks(@JsonProperty("payment_links") List<PaymentLink> paymentLinks) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PaymentLink(String id, String status, @JsonProperty("reference_id") String referenceId,
                       @JsonProperty("amount_paid") long amountPaid, List<Payment> payments) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Payment(@JsonProperty("payment_id") String paymentId) { }
}
