# Demo-day checklist (Ram's Windows laptop)

## The evening before

- [ ] Fill in the placeholders: **[team number]** and **[faculty name]** on slide 1, the report title page, and the README team table; fill in the contribution matrix in report §7.1.
- [ ] Run the whole demo once from `docs/Demo-Script.md`, start to finish, with a timer (target: under 8 minutes).
- [ ] Record that run as a screen video (Win + Alt + R) and copy it to a USB drive together with `docs/` (slides, report PDF, screenshots).
- [ ] Charge the laptop fully and pack the charger.
- [ ] Turn off Windows Update restarts for tomorrow: Settings → Windows Update → **Pause updates** for 1 week.
- [ ] Check that Smart App Control is off, or `c8run.exe` will be blocked (Windows Security → App & browser control).

## 30 minutes before

- [ ] Close every other program, especially anything using ports **8080, 26500, 8081, 3000, 5173, 1025, 8025** (if the Docker stack is running, stop it first with `docker compose down`).
- [ ] Turn on Do Not Disturb (Win + N → Do not disturb) so no pop-ups appear on the projector.
- [ ] Double-click **`start-demo.bat`**. Wait until the backend window shows `Started RevaluationBackendApplication` (about 1 minute; longer on the first run).
- [ ] Open the four tabs: `localhost:5173`, `localhost:8080/operate` (demo / demo), `localhost:8025`, `localhost:3000`.
- [ ] On the mock dashboard (`:3000`) all three switches say **up**.
- [ ] In Mailpit click **Delete all**.
- [ ] Quick smoke test: apply for `21CSC306L` only. Within a few seconds it shows **Rejected** and two emails arrive in Mailpit (the rejection and the summary). Delete them.
- [ ] Set browser zoom to 110 %. Open the slides in presenter view and check the speaker notes show on the laptop screen only.

## During the review

| Part | Who | Time |
|---|---|---|
| Slides 1–8 (problem, design, BPMN, constructs, errors) | Ram | about 8 min |
| Slides 9–15 (backend, portal, run, Docker, tests, next steps) | Surya | about 7 min |
| Live demo: Ram clicks, Surya narrates | both | about 8 min |
| Questions: process and BPMN go to Ram; code, React and Docker go to Surya | both | rest |

- Say plainly what was **not** done: there is no real login, the payment provider is a mock, and only the main and email-failure scenarios were repeated on Docker (all 12 ran on Camunda 8 Run).
- If something breaks: say "let me show the recorded evidence from the same run" and switch to the screenshots or the video (backup plan in `docs/Demo-Script.md`).

## After the review

- [ ] Run **`stop-demo.bat`** (stops the backend, Node, Mailpit and Camunda 8 Run).
