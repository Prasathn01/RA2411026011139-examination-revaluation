package in.srmist.revaluation.service;

import in.srmist.revaluation.dto.ProcessVariables.GenerateLetterInput;
import in.srmist.revaluation.dto.ProcessVariables.Letter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Builds the outcome letter (HTML, same layout as Review 2) and stores it in the document store
 * of the mock services. If the document store is down, the HTTP call throws, the job fails,
 * Camunda retries it, and after the last retry an incident appears in Operate.
 */
@Service
public class LetterService {

    private final RestClient mockServices;
    private final EventLog eventLog;

    public LetterService(RestClient mockServicesClient, EventLog eventLog) {
        this.mockServices = mockServicesClient;
        this.eventLog = eventLog;
    }

    public Letter generate(GenerateLetterInput v) {
        String requestId = v.application().requestId();
        String fileName = requestId + ".html";
        StoredLetter saved = mockServices.post().uri("/letters")
                .body(Map.of("fileName", fileName, "html", render(v), "requestId", requestId))
                .retrieve()
                .body(StoredLetter.class);
        eventLog.record(v.applicationId(), requestId, "WORKER", v.courseCode() + ": outcome letter stored at " + saved.url());
        return new Letter(saved.url(), saved.fileName(), LocalDate.now().toString());
    }

    record StoredLetter(String fileName, String url) { }

    private String render(GenerateLetterInput v) {
        boolean revised = "RESULT_REVISED".equals(v.decision().outcome());
        List<String[]> rows = List.of(
                new String[]{"Register number", v.registerNo()},
                new String[]{"Course", v.courseCode() + " - " + v.courseName()},
                new String[]{"Marks before revaluation", v.originalMarks() + " (" + v.originalGrade() + ")"},
                new String[]{"Final marks", v.decision().finalMarks() + " (" + v.decision().grade() + ", grade point " + v.decision().gradePoint() + ")"},
                new String[]{"Third valuation", Boolean.TRUE.equals(v.thirdValuationDone()) ? "Yes - marks differed by more than 15" : "Not required"},
                new String[]{"Fee refund", Boolean.TRUE.equals(v.decision().refundFee()) ? "Refunded (" + (v.refundId() == null ? "refund initiated" : v.refundId()) + ")" : "Not applicable"});
        StringBuilder table = new StringBuilder();
        for (String[] r : rows) {
            table.append("<tr><td>").append(esc(r[0])).append("</td><td>").append(esc(r[1])).append("</td></tr>");
        }
        String body = revised
                ? "Your answer script has been revalued and your marks are revised from <b>" + v.originalMarks() + "</b> to <b>" + v.decision().finalMarks() + "</b>."
                : "Your answer script has been revalued. There is no increase in your marks, so your original marks of <b>" + v.originalMarks() + "</b> stand.";
        return "<!doctype html><html><head><meta charset=\"utf-8\"><title>Revaluation outcome " + esc(v.application().requestId()) + "</title>"
                + "<style>body{font-family:Georgia,serif;max-width:720px;margin:40px auto;color:#13233F;line-height:1.5;padding:0 16px}"
                + "h1{font-size:22px;margin:0}.muted{color:#5A6270;font-size:14px}table{border-collapse:collapse;width:100%;margin:20px 0}"
                + "td{border-bottom:1px solid #DDD8CC;padding:8px 4px}td:first-child{color:#5A6270;width:45%}</style></head><body>"
                + "<p class=\"muted\">Office of the Controller of Examinations</p><h1>Revaluation outcome</h1>"
                + "<p class=\"muted\">Ref. " + esc(v.application().requestId()) + " - Issued " + LocalDate.now() + "</p>"
                + "<p>Dear " + esc(v.studentName()) + ",</p><p>" + body + "</p><table>" + table + "</table>"
                + "<p>" + (v.resultUpdatedAt() != null ? "The results system has been updated." : "The Examination Cell has updated your result manually.") + "</p>"
                + "<p>Controller of Examinations</p></body></html>";
    }

    private static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s);
    }
}
