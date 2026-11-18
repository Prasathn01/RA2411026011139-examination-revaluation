package in.srmist.revaluation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request bodies for the staff portal endpoints. */
public final class TaskRequests {

    private TaskRequests() { }

    /** POST /api/auth/login */
    public record Login(@NotBlank @Size(max = 40) String username, @NotBlank @Size(max = 100) String password) { }

    /**
     * POST /api/tasks/{taskKey}/complete.
     * One body type for every task; TaskService checks which fields each task needs
     * (for example "Revalue answer script" needs revaluedMarks, "CoE assigns examiner" needs
     * selectedExaminerId, or examinerName and examinerEmail to add an external examiner).
     */
    public record Complete(
            @Min(0) @Max(100) Integer revaluedMarks,
            @Size(max = 500) String examinerRemarks,
            @Min(0) @Max(100) Integer thirdValuationMarks,
            @Size(max = 500) String thirdValuationRemarks,
            @Size(max = 20) String selectedExaminerId,
            @Size(max = 80) String examinerName,
            @Email @Size(max = 120) String examinerEmail,
            @Size(max = 500) String actionTaken,
            Boolean actionCompleted) { }
}
