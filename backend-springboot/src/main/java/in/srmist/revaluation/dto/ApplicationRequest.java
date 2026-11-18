package in.srmist.revaluation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Body of POST /api/applications, sent by the React "Apply" page.
 * The courses list becomes the process variable "courses", which drives the parallel multi-instance.
 *
 * paymentWindow and examinerDeadline are optional demo overrides in ISO-8601 duration format
 * (for example PT1M = one minute). Without them the process uses 48 h and 72 h.
 */
public record ApplicationRequest(
        @NotBlank @Pattern(regexp = "^RA[0-9]{13}$", message = "must look like RA2411026011139")
        String registerNo,

        @NotBlank @Size(max = 80)
        String studentName,

        @NotBlank @Email(message = "must be a valid email address")
        String studentEmail,

        @NotEmpty(message = "add at least one course") @Size(max = 6, message = "at most 6 courses per application")
        List<@NotBlank @Pattern(regexp = "^[0-9]{2}[A-Z]{3}[0-9]{3}[A-Z]$", message = "course code must look like 21CSC305P") String> courses,

        @Pattern(regexp = "^PT[0-9]+[SMH]$", message = "must be an ISO duration such as PT1M")
        String paymentWindow,

        @Pattern(regexp = "^PT[0-9]+[SMH]$", message = "must be an ISO duration such as PT1M")
        String examinerDeadline) {
}
