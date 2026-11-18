// FEEL check: reads every important FEEL expression straight out of the BPMN files (so this file
// can never drift from the models), evaluates it with a sample input using the "feelin" FEEL
// interpreter, and prints input -> output. These outputs are the worked examples in the README
// FEEL dictionary.
//
//   cd scripts
//   npm install
//   npm run feel
//
// Note: Camunda adds a few functions to standard FEEL. "get or else(value, default)" is one; it is
// supplied below. The request-ID timestamp suffix in Task_Register uses date-and-time arithmetic
// that feelin does not support, so that one part is checked against real engine runs instead.
import { evaluate } from 'feelin';
import { readFileSync } from 'node:fs';

const models = new URL('../bpmn-models/', import.meta.url);
const xml = Object.fromEntries(['revaluation-application', 'course-revaluation', 'notify-stakeholder']
  .map((n) => [n, readFileSync(new URL(`${n}.bpmn`, models), 'utf8')]));

const unescape = (s) => s.replace(/&quot;/g, '"').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&#10;/g, '\n').replace(/&amp;/g, '&');

/** Finds one expression: the element's XML, then a script, condition, timer, mapping target or header. */
function expr(file, id, what) {
  const x = xml[file];
  const start = x.indexOf(` id="${id}"`);
  if (start < 0) throw new Error(`${id} not found in ${file}`);
  // The element's own XML: from its id up to the next element that has an id of its own
  const next = x.slice(start + 1).search(/<bpmn:(?!conditionExpression|timeDuration|extensionElements|incoming|outgoing|multiInstanceLoopCharacteristics|errorEventDefinition|timerEventDefinition|messageEventDefinition)\w+ id="/);
  const el = next < 0 ? x.slice(start) : x.slice(start, start + 1 + next);
  let m;
  if (what === 'script') m = el.match(/<zeebe:script expression="([^"]*)"/);
  else if (what === 'condition') m = el.match(/<bpmn:conditionExpression[^>]*>([^<]*)</);
  else if (what === 'timer') m = el.match(/<bpmn:timeDuration[^>]*>([^<]*)</);
  else if (what === 'correlation') m = el.match(/correlationKey="([^"]*)"/);
  else if (what.startsWith('header:')) m = el.match(new RegExp(`key="${what.slice(7)}" value="([^"]*)"`));
  else m = el.match(new RegExp(`source="([^"]*)" target="${what.replace('.', '\\.')}"`));   // input/output mapping target
  if (!m) throw new Error(`${what} not found on ${id}`);
  return unescape(m[1]).replace(/^=/, '');
}

// "bpmnError" exists only in the connector runtime's error expressions; this stand-in returns what it was called with.
const camunda = {
  'get or else': (value, fallback) => (value === null || value === undefined ? fallback : value),
  bpmnError: (code, message, variables) => ({ code, message, ...(variables ? { variables } : {}) }),
};
let failures = 0;

function check(label, file, id, what, context, expected) {
  const source = expr(file, id, what);
  // Camunda accepts a real line break inside a FEEL string; feelin only accepts the \n escape (same meaning)
  const { value } = evaluate(source.replace(/\n/g, '\\n'), { ...camunda, ...context });
  const ok = expected === undefined || JSON.stringify(value) === JSON.stringify(expected);
  if (!ok) failures++;
  console.log(`\n${ok ? 'OK  ' : 'FAIL'} ${label}   [${file} / ${id} / ${what}]`);
  console.log(`  FEEL:   ${source.replace(/\n/g, '\\n')}`);
  console.log(`  input:  ${JSON.stringify(context)}`);
  console.log(`  output: ${JSON.stringify(value)}${ok ? '' : `   expected ${JSON.stringify(expected)}`}`);
}

// ---------- revaluation-application.bpmn (parent) ----------
check('Course list check (valid)', 'revaluation-application', 'Task_CheckApplication', 'script',
  { courses: ['21CSC305P', '21CSE251T'] }, { courseCount: 2, hasDuplicates: false, valid: true });
check('Course list check (duplicate)', 'revaluation-application', 'Task_CheckApplication', 'script',
  { courses: ['21CSC305P', '21CSC305P'] }, { courseCount: 2, hasDuplicates: true, valid: false });
check('Gateway "Course list valid?"', 'revaluation-application', 'Flow_ListValid', 'condition',
  { applicationCheck: { valid: true } }, true);

const results = [
  { courseCode: '21CSC305P', status: 'CHANGED', reason: 'Revaluation completed', finalMarks: 74, grade: 'A', refundAmount: 1000 },
  { courseCode: '21CSC306L', status: 'REJECTED', reason: 'Practical, project and internal assessments are not revalued', finalMarks: null, grade: null, refundAmount: 0 },
];
check('Summarise application', 'revaluation-application', 'Task_Summarise', 'script', { courseResults: results },
  { total: 2, changed: 1, unchanged: 0, rejected: 1, cancelled: 0, totalRefund: 1000,
    lines: '21CSC305P: CHANGED (final marks 74, grade A)\n21CSC306L: REJECTED - Practical, project and internal assessments are not revalued' });
check('Gateway "Any marks changed?"', 'revaluation-application', 'Flow_Changed', 'condition', { summary: { changed: 1 } }, true);
check('Recipients when marks changed (sequential multi-instance list)', 'revaluation-application', 'Task_PrepareRevised', 'script',
  { studentName: 'Ram Prasath N', studentEmail: 'ram@example.com', hodEmail: 'hod.cse@srmist.test', coeEmail: 'coe@srmist.test' },
  [{ role: 'STUDENT', name: 'Ram Prasath N', email: 'ram@example.com' },
   { role: 'HOD', name: 'Head of Department', email: 'hod.cse@srmist.test' },
   { role: 'COE', name: 'Controller of Examinations', email: 'coe@srmist.test' }]);
check('Summary email subject', 'revaluation-application', 'Call_NotifyStakeholders', 'subject',
  { applicationId: 'APP-261118081942740' }, 'Revaluation application APP-261118081942740 - summary');

// ---------- course-revaluation.bpmn (child, one instance per course) ----------
check('Results API URL', 'course-revaluation', 'Task_FetchResult', 'url',
  { camunda: { secrets: { RESULTS_API_URL: 'http://localhost:3000' } }, registerNo: 'RA2411026011139', courseCode: '21CSC305P' },
  'http://localhost:3000/results/RA2411026011139-21CSC305P');
check('Fetch result: 404 becomes RESULT_NOT_FOUND', 'course-revaluation', 'Task_FetchResult', 'header:errorExpression',
  { error: { code: '404' } }, { code: 'RESULT_NOT_FOUND', message: 'No published result for this course' });
check('Gateway "Eligible?"', 'course-revaluation', 'Flow_Eligible', 'condition', { eligibility: { eligible: true } }, true);
check('Payment link request body', 'course-revaluation', 'Task_PaymentLink', 'body',
  { eligibility: { fee: 1000 }, courseCode: '21CSC305P', application: { requestId: 'REV-RA2411026011139-21CSC305P-44582966' },
    studentName: 'Ram Prasath N', studentEmail: 'ram@example.com' },
  { amount: 100000, currency: 'INR', description: 'Revaluation fee - 21CSC305P', reference_id: 'REV-RA2411026011139-21CSC305P-44582966',
    customer: { name: 'Ram Prasath N', email: 'ram@example.com' }, notify: { email: true } });
check('Payment link: HTTP 503 becomes PAYMENT_FAILED', 'course-revaluation', 'Task_PaymentLink', 'header:errorExpression',
  { error: { code: '503' } }, { code: 'PAYMENT_FAILED', message: 'Payment service returned HTTP 503' });
check('Payment window timer (no override)', 'course-revaluation', 'TimerDef_PaymentDeadline', 'timer', {}, 'PT48H');
check('Payment window timer (demo override)', 'course-revaluation', 'TimerDef_PaymentDeadline', 'timer', { paymentWindow: 'PT1M' }, 'PT1M');
check('Message correlation key', 'course-revaluation', 'Message_FeePaid', 'correlation',
  { application: { requestId: 'REV-RA2411026011139-21CSC305P-44582966' } }, 'REV-RA2411026011139-21CSC305P-44582966');
const ravi = { id: 'EXM-103', name: 'Dr. C. Ravi', email: 'c.ravi@examiners.test', username: 'exm-103' };
check('Revaluer from the login worker (output mapping)', 'course-revaluation', 'Task_SendLogin', 'examiner', { assignedExaminer: ravi }, ravi);
check('Third examiner from the same worker (output mapping)', 'course-revaluation', 'Task_SendLoginThird', 'thirdExaminer', { assignedExaminer: ravi }, ravi);
check('Third valuation replaces the revalued marks (output mapping)', 'course-revaluation', 'Task_ThirdValuation', 'revaluedMarks', { thirdValuationMarks: 52 }, 52);
check('Compare marks', 'course-revaluation', 'Task_CompareMarks', 'script', { revaluedMarks: 66, originalMarks: 47 }, 19);
check('Gateway "Third valuation needed?"', 'course-revaluation', 'Flow_ThirdNeeded', 'condition', { decision: { outcome: 'THIRD_VALUATION' } }, true);
check('Examiner deadline timer (no override)', 'course-revaluation', 'TimerDef_ExaminerDeadline', 'timer', {}, 'PT72H');
check('Gateway "Fee refundable?"', 'course-revaluation', 'Flow_Refund', 'condition', { decision: { refundFee: true } }, true);
check('Refund URL', 'course-revaluation', 'Task_Refund', 'url',
  { camunda: { secrets: { PAYMENT_API_URL: 'http://localhost:3000/razorpay/v1' } }, paymentId: 'pay_123' },
  'http://localhost:3000/razorpay/v1/payments/pay_123/refund');
check('Record course result (changed)', 'course-revaluation', 'Task_RecordOutcome', 'script',
  { courseCode: '21CSC305P', courseName: 'Machine Learning', application: { requestId: 'REV-1' }, decision: { outcome: 'RESULT_REVISED', finalMarks: 74, refundFee: true },
    originalMarks: 68, eligibility: { fee: 1000 }, examiner: { name: 'Dr. C. Ravi' }, letter: { url: 'http://localhost:3000/letters/x.html' } },
  { courseCode: '21CSC305P', courseName: 'Machine Learning', requestId: 'REV-1', status: 'CHANGED', reason: 'Revaluation completed',
    originalMarks: 68, finalMarks: 74, grade: null, refundAmount: 1000, examinerName: 'Dr. C. Ravi', letterUrl: 'http://localhost:3000/letters/x.html' });
check('Record course result (grade from the outcome DMN, after a third valuation)', 'course-revaluation', 'Task_RecordOutcome', 'script',
  { courseCode: '21CSC303J', courseName: 'Operating Systems', application: { requestId: 'REV-2' }, decision: { outcome: 'RESULT_REVISED', finalMarks: 52, refundFee: true, grade: 'C' },
    originalMarks: 47, thirdValuationDone: true, eligibility: { fee: 1000 }, examiner: { name: 'Dr. B. Priya' }, thirdExaminer: { name: 'Dr. A. Kumar' }, letter: { url: 'http://localhost:3000/letters/y.html' } },
  { courseCode: '21CSC303J', courseName: 'Operating Systems', requestId: 'REV-2', status: 'CHANGED', reason: 'Revaluation completed',
    originalMarks: 47, finalMarks: 52, grade: 'C', refundAmount: 1000, examinerName: 'Dr. B. Priya (third valuation: Dr. A. Kumar)', letterUrl: 'http://localhost:3000/letters/y.html' });
check('Record rejection when the result was not found (no eligibility yet)', 'course-revaluation', 'Task_RecordRejection', 'script',
  { courseCode: '21CSC304T', courseName: null, originalMarks: null });
check('requestId passed to notify-stakeholder (null before registration)', 'course-revaluation', 'Call_NotifyRejection', 'requestId', {}, null);

// ---------- notify-stakeholder.bpmn ----------
check('Email failure becomes EMAIL_FAILED with the message as a variable', 'notify-stakeholder', 'Task_SendEmail', 'header:errorExpression',
  { error: { message: 'Connection refused' } },
  { code: 'EMAIL_FAILED', message: 'Connection refused', variables: { emailError: 'Connection refused' } });
check('Email success raises no error', 'notify-stakeholder', 'Task_SendEmail', 'header:errorExpression', { error: null }, null);

console.log(failures ? `\n${failures} check(s) FAILED` : '\nAll checks passed.');
process.exit(failures ? 1 : 0);
