// Courses known to the mock results system, offered as suggestions in the course builder.
// Students may also type any other code; an unknown code ends as "Result not found".
export const TEST_COURSES = [
  { code: '21CSC305P', name: 'Machine Learning', note: 'theory - eligible' },
  { code: '21CSE251T', name: 'Artificial Intelligence', note: 'theory - eligible' },
  { code: '21CSC303J', name: 'Operating Systems', note: 'publishing fails - CoE updates by hand' },
  { code: '21CSE735P', name: 'Workflow Automation Engineering', note: 'no other examiner - CoE adds an external one' },
  { code: '21CSC306L', name: 'Machine Learning Lab', note: 'practical - not eligible' },
  { code: '21MAB201T', name: 'Probability and Statistics', note: 'result 12 days old - window closed' },
  { code: '21CSC304T', name: 'Compiler Design', note: 'result not published' },
]

export const COURSE_PATTERN = /^[0-9]{2}[A-Z]{3}[0-9]{3}[A-Z]$/
