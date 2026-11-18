# Viva preparation sheet

36 likely questions, each with a short answer grounded in this project's code and a concrete example. Read the **answer** aloud as it is; the **example** is what to point at if the examiner asks "show me".

Abbreviations used: BPMN (Business Process Model and Notation), DMN (Decision Model and Notation), FEEL (Friendly Enough Expression Language), JPA (Jakarta Persistence API), REST (Representational State Transfer), SMTP (Simple Mail Transfer Protocol), CoE (Controller of Examinations), HOD (Head of Department).

---

## A. The process design

**1. Why did you split the work into three BPMN processes?**
Each process has one job. `revaluation-application` handles the whole application. `course-revaluation` handles one course from start to finish. `notify-stakeholder` sends one email and handles its failure. The course process is long and has many branches, so keeping it separate makes both models readable, and the notification process is reused in four places.
*Example:* the parent calls `course-revaluation` once per course, and both the parent and the child call `notify-stakeholder`.

**2. What is a call activity?**
A call activity is a task that starts another, separately deployed process and waits for it to finish. The called process has its own instance, its own variables and its own history in Operate.
*Example:* `Call_RevaluateCourse` starts process ID `course-revaluation`. In Operate the parent instance lists its child instances as "called instances".

**3. What is the difference between parallel and sequential multi-instance?**
Both run the same activity once for every item in a list. Parallel starts all of them at the same moment. Sequential starts the next one only after the previous one has finished.
*Example:* `Call_RevaluateCourse` is parallel over `courses`, so 3 courses give 3 child instances at once. `Call_NotifyStakeholders` is sequential over the recipient list, so the HOD gets the summary only after the student's email has gone out.

**4. Why is the notification sequential and not parallel?**
The order matters to the business: the student should hear the result first, and the HOD and CoE afterwards. Sequential also means a failure in the first email is recorded before the next one starts.
*Example:* in Mailpit the three summary emails arrive student → HOD → CoE.

**5. What is an embedded sub-process, and why do you use two?**
A sub-process groups several steps inside the same process, so a single boundary event can cover all of them. `Sub_FeePayment` groups "create payment link" and "wait for payment". `Sub_Revaluation` groups examiner assignment, revaluation, comparison and the optional third valuation.
*Example:* the 48-hour timer is attached to the whole `Sub_FeePayment`, so it fires no matter which step inside is waiting.

**6. What is a boundary event? Name the ones in your models.**
A boundary event is attached to the edge of a task or sub-process and catches something that happens while that element is active. We have seven: `Boundary_ResultNotFound`, `Boundary_PaymentDeadline` (timer), `Boundary_PaymentFailed`, `Boundary_NoExaminer`, `Boundary_ExaminerDeadline` (timer, non-interrupting), `Boundary_PublishFailed` and `Boundary_EmailFailed`.

**7. Interrupting or non-interrupting: what is the difference?**
An interrupting boundary event cancels the element it is attached to and continues on its own path. A non-interrupting one starts its own path while the element keeps running.
*Example:* `Boundary_PaymentDeadline` is interrupting: after 48 hours the payment wait is cancelled and the course is recorded as cancelled. `Boundary_ExaminerDeadline` is non-interrupting: after 72 hours the CoE gets an escalation task, but the examiner's task stays open.

**8. How does the process wait for the payment?**
An intermediate message catch event "Fee paid" waits for a message named `RevaluationFeePaid` whose correlation key equals the variable `application.requestId`. The backend publishes that message when it sees the payment link was paid.
*Example:* request ID `REV-RA2411026011139-21CSC305P-44582966`. Three courses waiting at the same time each have a different request ID, so each message reaches exactly one course.

**9. Why does the backend poll for payments instead of receiving a webhook?**
A webhook needs a public URL that the payment provider can call, and a laptop demo does not have one. So `PaymentService` reads the payment links every 5 seconds (`@Scheduled(fixedDelay = 5000)`) and publishes the message for each newly paid link. In production this would be replaced by the Razorpay webhook calling the same publish code.

**10. How does the third-valuation loop work, and why can it not loop forever?**
After the examiner enters marks, "Compare marks" computes the difference and the outcome DMN decides. If `thirdValuationDone` is false and the absolute difference is more than 15, the outcome is `THIRD_VALUATION`, the third valuation task runs, and the flow goes back to the comparison. The third valuation task's output mapping sets `thirdValuationDone` to `true` and replaces `revaluedMarks` with the third mark, so the second time round the rule cannot match.
*Example:* published 47, revalued 66: difference 19 → third valuation 52 → final 52, grade C.

---

## B. Decisions and FEEL

**11. What do your three DMN tables decide?**
`revaluation-eligibility` decides whether the course can be revalued and the fee. `revaluation-outcome` decides third valuation, result revised (with refund) or no change. `marks-to-grade` turns final marks into a grade.
*Example:* a `THEORY` course published 2 days ago gives eligible, fee 1000. Final marks 74 give grade A.

**12. What is a hit policy? Which ones do you use?**
The hit policy says what happens when more than one rule matches. `FIRST` returns the first matching rule from the top, so the order of rules matters. `UNIQUE` means only one rule may ever match. Eligibility and outcome use `FIRST`; grading uses `UNIQUE`, because the mark ranges do not overlap.
*Example:* in eligibility, "result not published" is checked before the course type, so an unpublished result is rejected for that reason first.

**13. What exactly are the eligibility rules?**
Checked in order: days since publication less than 0 → not published; more than 7 → window closed; course type `THEORY` or `LAB_INTEGRATED_THEORY` → eligible with fee ₹1000; anything else → rejected as practical.
*Example:* `21MAB201T` was published 12 days ago, so it is rejected because the window closed.

**14. What is FEEL? Give one expression from your project.**
FEEL is the expression language Camunda uses for conditions, mappings and scripts. An expression starts with `=`.
*Example:* the gateway "Eligible?" uses `=eligibility.eligible = true`. The REST connector URL is `=camunda.secrets.RESULTS_API_URL + "/results/" + registerNo + "-" + courseCode`.

**15. How did you check that your FEEL expressions are correct?**
`scripts/feel-check.mjs` reads each expression directly out of the BPMN files and evaluates it with sample inputs using the `feelin` interpreter, then compares the output with the expected value. 26 checks pass. Because it reads the BPMN files, the check cannot drift from the models.
*Example:* "Compare marks" with `revaluedMarks = 66` and `originalMarks = 47` must return 19.

**16. What does `get or else` do?**
It returns the first argument, or the second one if the first is null. It is a Camunda extension to FEEL.
*Example:* `get or else(error.message, "Email could not be sent")` gives a readable message even when the mail error has no text.

---

## C. Connectors, errors and secrets

**17. What is a connector? Which ones do you use?**
A connector is a ready-made service task run by Camunda's connectors runtime, so no Java code is needed for it. We use the REST connector four times (fetch result, create payment link, publish result, refund) and the Email connector once (send email).

**18. How does a REST connector turn an HTTP error into a BPMN error?**
Through its `errorExpression` header, a FEEL expression that runs when the call fails and may return `bpmnError(code, message)`.
*Example:* `Task_FetchResult` has `=if error.code = "404" then bpmnError("RESULT_NOT_FOUND", "No published result for this course") else null`. A 404 becomes a BPMN error caught by `Boundary_ResultNotFound`; any other failure is retried and then becomes an incident.

**19. What is the difference between a BPMN error and an incident?**
A BPMN error is an expected business failure that the model has a path for, so the process continues on that path. An incident is an unexpected technical failure: the job is retried (3 times in our models) and then Camunda stops at that element and shows an incident in Operate until someone fixes the cause and clicks Retry.
*Example:* "no examiner free" is a BPMN error (`NO_EXAMINER_AVAILABLE`). The document store being down while generating the letter is an incident.

**20. How do you keep secrets out of the BPMN files?**
The models refer to `camunda.secrets.NAME`. Just before handing the job to the connector, the Camunda engine looks the value up in its secret store and fills it in; the value is never saved in process variables, Operate or logs. Our store is the file store: a folder with one file per secret. Locally `c8run secrets import secrets\secrets.env` fills it; in Docker the `camunda-data-init` container writes the files from `.env`. Neither `secrets.env` nor `.env` is committed to git.
*Example:* `SMTP_PASSWORD`, `RAZORPAY_KEY_SECRET`, `RESULTS_API_URL`.

**21. What went wrong with secrets when you first ran Docker, and how did you fix it?**
The first Docker run raised a `SECRET_RESOLUTION_ERROR` incident on "Fetch published result": "No secret store is configured". We had given the values to the connectors container as `SECRET_*` environment variables, but in Camunda 8.10 the engine resolves `camunda.secrets.*`, not the connectors runtime, and Self-Managed supports only a file, AWS or GCP store. Locally this was hidden, because `c8run secrets import` fills the engine's file store. The fix: `camunda-data-init` writes the files into a shared volume, and `camunda/application.yaml` sets `camunda.secrets.stores.file.default.path`.
*Example:* `POST /v2/secrets/list` now returns the 8 secret names, and the same scenario completes.

**22. When the email fails, how does the error message reach your log?**
The error message is not available as a variable at the boundary event by default. So the error expression passes it as a variable in the third argument: `bpmnError("EMAIL_FAILED", msg, {emailError: msg})`. The boundary event maps `emailError`, and the `revaluation.log-notification-failure` worker writes it to the event log.
*Example:* with Mailpit stopped, the timeline shows "Email NOT sent … Connection refused", and the instance still completes.

---

## D. Variables and scoping

**23. What is variable scoping and how did you control it?**
Every process instance, sub-process and multi-instance item has its own scope of variables. On each call activity we set `propagateAllChildVariables="false"` and list the input and output mappings explicitly, so a child only receives what it needs and only returns its result.
*Example:* each course child returns only `courseResult`, which the parent collects into the list `courseResults`. Without this, three children writing `finalMarks` would overwrite each other in the parent.

**24. What are `inputElement` and `outputCollection` in a multi-instance?**
`inputCollection` is the list to loop over and `inputElement` is the name of one item inside each instance. `outputElement` is what each instance produces and `outputCollection` is the list those values are collected into, in the same order as the input.
*Example:* input `courses`, element `courseCode`; output element `courseResult`, collection `courseResults`.

---

## E. Spring Boot backend

**25. What is a job worker?**
A job worker is Java code that Camunda calls when a process reaches a service task of a given type. It receives the variables, does the work and returns new variables, or throws a BPMN error.
*Example:* `@JobWorker(type = "revaluation.assign-examiner")` in `AssignExaminerWorker`.

**26. Which four workers did you write, and why not more?**
Assign examiner, generate letter, save results and log notification failure. Everything else (HTTP calls, email, decisions, scripts) is done by connectors, DMN or FEEL, which need no Java. We wrote Java only where the logic needs our database.
*Example:* assigning an examiner needs the examiner table and the count of open scripts, so it is a worker.

**27. How does the examiner assignment work?**
`ExaminerService.assign` takes the available examiners ordered by how many scripts they already have, keeps only those allowed to value this course, removes the examiner who marked the paper first, and picks the first one left. If none is left, the worker throws `NO_EXAMINER_AVAILABLE`. It is also idempotent: if Camunda hands the same job out twice, the course keeps the examiner it already has.
*Example:* for `21CSC305P` the first examiner `EXM-101` is excluded, so Dr. C. Ravi was chosen.

**28. What does "typed" job worker mean?**
The worker's input is a Java record, and Spring maps the process variables into it by name. A missing or wrongly typed variable fails clearly at that point, instead of failing later deep in the code.
*Example:* `ProcessVariables.NotificationFailureInput(applicationId, requestId, recipient, subject, emailError)`.

**29. Describe the backend layers.**
Controller receives HTTP and validates the request. Service holds the logic. Repository reads and writes the database using JPA. Entity is the Java class mapped to a table. There are 3 controllers with 11 endpoints, 8 services, 4 repositories and 4 entities.
*Example:* `POST /api/applications` → `ApplicationController` → `ApplicationService` saves a `RevaluationApplication` and starts the process.

**30. How is input validated?**
With Jakarta Bean Validation annotations on the request record, checked automatically by Spring, and the same rules in the React form. A failed check returns HTTP 400 with a message per field.
*Example:* `@Pattern(regexp = "^RA[0-9]{13}$")` on the register number; `@Size(max = 6)` on the course list. Sending marks of 140 to complete a task returns `fieldErrors.revaluedMarks`.

**31. Which database do you use?**
PostgreSQL 16 in Docker. When the backend runs locally without Docker, it uses an H2 file database in `backend-springboot/data/`, so the demo needs no database installation. The switch is just the `DB_URL` environment variable.

**32. What do your tests cover?**
12 JUnit tests: the course list validator (3), examiner selection with Mockito (4), the assign-examiner worker throwing the right BPMN error (2), and the application controller with `@WebMvcTest` (3: created, field errors, bad course code). Run them with `mvn test`.

---

## F. React portal

**33. How does the React portal talk to Camunda?**
It never talks to Camunda directly. It calls our backend (`/api/...`), and the backend uses the Camunda client. This keeps Camunda's address and any credentials out of the browser, and lets the backend check every value before a task is completed.

**34. How does the live tracker know where a course is?**
It polls `GET /api/applications/{id}` and the timeline endpoint every few seconds. The diagram is drawn with `bpmn-js` from the deployed model (`GET /api/models/{processId}`), and the active element is highlighted.

**35. Why must a task be claimed before completing it?**
Claiming assigns the task to one person, so two examiners cannot complete the same script. The backend refuses to complete an unclaimed task with "Claim the task before completing it." Camunda's task search lags about one second behind the engine, so if someone completes within a second of claiming, they may see that message once; waiting a moment and trying again works.

**36. What are the limitations you would fix next?**
No real login (choosing a role only filters tasks); only two scenarios were repeated on Docker; the payment provider is a mock; and there are no automated tests that run the BPMN models end to end (the next step would be Camunda Process Test).
