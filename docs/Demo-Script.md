# Live demo script (about 8 minutes)

This is the click-by-click plan for the live demonstration at the final review. It runs on **Camunda 8 Run 8.10.6** on Ram's Windows laptop, started with `start-demo.bat`. Camunda 8 Run is the setup on which all 12 scenarios were rehearsed, so use it for the demo. The Docker setup also works (tested from a fresh copy on 18 Nov 2026); if the panel asks to see it, run `docker compose up -d` after `stop-demo.bat`, because both use the same ports. The same clicks then work on the same URLs.

**Who does what:** Ram drives the mouse and keyboard the whole time. Surya speaks the narration (the lines in quotes). This keeps one pair of hands on the laptop, so nothing gets clicked twice.

**One application covers almost every feature.** We apply for three courses at once:

| Course | What it shows |
|---|---|
| `21CSC305P` Machine Learning | the normal path: pay, revalue (68 → 74), grade A, fee refunded |
| `21CSC303J` Operating Systems | third valuation (47 → 66, a difference of 19, which is more than 15), then a publishing failure that the Exam Cell fixes by hand |
| `21CSC306L` Machine Learning Lab | a practical course, so the eligibility table (DMN, Decision Model and Notation) rejects it at once |

---

## Before the panel arrives (15 minutes before)

1. Double-click `start-demo.bat` and wait for the four windows: Mailpit, mock services, backend and React. The backend is ready when its window shows `Started RevaluationBackendApplication`.
2. Open these browser tabs, in this order, and leave them open:
   1. `http://localhost:5173` (the portal, Apply page)
   2. `http://localhost:8080/operate` (sign in with `demo` / `demo`)
   3. `http://localhost:8025` (Mailpit, the test inbox)
   4. `http://localhost:3000` (mock services dashboard; check that all three switches say **up**)
3. In Mailpit, click **Delete all** so only the new emails appear during the demo.
4. Set the browser zoom to 110 % so the panel can read the screen.
5. Open `docs/Final-Review-Slides.pptx` in a separate window for the talk.

---

## The demo

### 1. Apply (about 1 min 15 s) · tab: Portal → Apply

| Ram clicks / types | Surya says |
|---|---|
| Type `RA24` in **Register number** and `ram@` in **Email**. | "The form checks every field as you type. A register number must be RA followed by 13 digits." |
| Point at the red messages. Then type the full register number `RA2411026011139`, name `Ram Prasath N`, email `ram@example.com`. | "The backend runs the same checks again, so a bad request cannot get in through the API either." |
| In **Courses**, type `21CSC305P`. Click **+ Add course**, type `21CSC305P` again. | "The course list is an array that the student grows. A duplicate is flagged straight away." |
| Change the second row to `21CSC303J`. Click **+ Add course**, type `21CSC306L`. | "Three courses in one application. Each one becomes its own child process." |
| Click **Submit application (3 courses)**, then **Track status**. | "The backend saves the application and starts the parent process in Camunda." |

### 2. Tracker and parallel instances (about 1 min) · tabs: Portal, then Operate

| Ram clicks | Surya says |
|---|---|
| On the tracker, wait 2 to 3 seconds. Point at `21CSC306L`, which shows **Rejected**. | "The lab course was rejected by the eligibility decision table: practical courses are not revalued. The student gets an email saying why." |
| Click **Show in diagram** on `21CSC305P`. | "The diagram is the deployed BPMN model. The highlighted element is where this course is right now: waiting for the fee." |
| Switch to **Operate** → **Processes** → open the newest **Revaluation application** instance. | "In Camunda Operate, one parent instance has started three child instances at the same time. That is the parallel multi-instance call activity." |

### 3. Pay the fee (about 45 s) · tab: Portal

| Ram clicks | Surya says |
|---|---|
| On the `21CSC305P` card click **Open payment link** → on the mock page click **Pay with UPI (test)**. Close that tab. | "The payment link was created by the REST connector against a Razorpay-shaped mock." |
| Do the same for `21CSC303J`. | "Every 5 seconds the backend checks for paid links and publishes a 'fee paid' message. The correlation key is the request ID, so each message reaches exactly one course." |
| Wait about 5 seconds. Both cards move to **Under revaluation**. | "Payment received, and an examiner has been assigned automatically: the least busy qualified examiner, never the one who marked the paper first." |

### 4. Examiner work (about 2 min 30 s) · tab: Portal → Task inbox

| Ram clicks / types | Surya says |
|---|---|
| **Task inbox**, role **Examiner**. Open the `21CSC305P` task. | "Notice the original marks are not shown. This is a blind revaluation." |
| Click **Claim as b.priya**. Type `74` in **Revalued marks**, remark `Q4 under-marked`. Click **Complete task**. | "A task must be claimed first, so two examiners cannot work on the same script." |
| Open the `21CSC303J` task, claim, type `140`, click **Complete task**. | "Marks must be a whole number from 0 to 100." |
| Change it to `66` and complete. | "The published mark was 47. The difference is 19, which is more than 15, so the outcome table asks for a third valuation." |
| Change the role to **Senior examiner (third valuation)**. Open **Third valuation**, claim, type `52`, complete. | "Only the senior-examiners group sees this task. The third valuation mark is final." |
| Change the role to **Examination Cell**. Wait up to 5 seconds for **Update result manually** to appear. Open it, claim, type `Entered 52 (C) in the results portal`, tick **Done**, complete. | "Publishing this result to the university system failed on purpose. The error boundary event sent it to the Exam Cell instead of crashing the process." |

### 5. Emails and the finished application (about 1 min 15 s) · tabs: Mailpit, Portal, Operate

| Ram clicks | Surya says |
|---|---|
| Switch to **Mailpit**. Point at the emails from top to bottom. | "These are sent by the Email connector. The SMTP server address and password come from connector secrets, not from the model." |
| Point at the three **summary** emails. | "The summary went to the student, then the head of department, then the Controller of Examinations, in that order. That is the sequential multi-instance." |
| Switch to the **Portal** tracker. | "Both courses went up, so both fees are refunded: ₹2000 in total. The timeline lists every step the workers recorded." |
| Switch to **Operate**, refresh the parent instance. | "The parent instance is completed. That ends the demo." |

**Total: about 7 to 8 minutes.** If the panel asks for more, use the extras below.

---

## Extras, only if asked (about 1 minute each)

- **Incident and retry.** Mock dashboard → click **lettersDown** to break it. Run `21CSE251T` through to the letter step. Operate shows an incident after 3 retries. Click **lettersDown** again to restore it, then **Retry** in Operate.
- **No free examiner.** Apply for `21CSE735P` and pay. The Exam Cell gets **Assign examiner manually**.
- **Timer.** Apply for `21CSC305P` with **Demo options → Payment window: 1 minute** and do not pay. After one minute the course shows **Cancelled** and a cancellation email arrives.

---

## Backup plan

**If something fails during the demo, do not debug in front of the panel.** Say: "Let me show you the recorded evidence from the same run," and move on.

1. **A single step is slow** (a task does not appear): wait 5 seconds and click **Refresh**. The task search lags about one second behind the engine.
2. **One component has crashed** (a window has closed or shows an error): run `stop-demo.bat`, then `start-demo.bat`. This takes about 2 minutes. Fill the time with slides 9 to 12.
3. **The laptop or Camunda will not start at all:** present the real screenshots from `docs/screenshots`, in this order:

   | Step | Screenshot |
   |---|---|
   | Apply + validation | `11a-react-apply-validation.jpg` |
   | Tracker, waiting for payment | `11b-react-tracker-awaiting-payment.jpg`, `11c-react-tracker-diagram-awaiting-payment.jpg` |
   | Under revaluation | `11d-react-tracker-under-revaluation.jpg` |
   | Blind revaluation, validation, claimed | `11e`, `11f`, `11g` |
   | Third valuation | `11h-react-inbox-third-valuation.jpg` |
   | Manual update by the Exam Cell | `11i-react-inbox-manual-update.jpg` |
   | Completed tracker | `11j-react-tracker-completed.jpg` |
   | Operate: parent and child instances | `01`, `02`, `03` |
   | Mailpit | `04-mailpit-inbox.png` |
   | Incident | `09a-operate-incident-generate-letter.png` |

4. **Recommended:** record a screen video of this script the evening before (Windows: **Win + Alt + R** with Xbox Game Bar). No recording exists yet; this has to be done on Ram's laptop.
