import { useState } from 'react'
import { Link } from 'react-router'
import { api } from '../api.js'
import { COURSE_PATTERN, TEST_COURSES } from '../courses.js'
import { useToast } from '../components/Toast.jsx'

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const REGISTER_NO = /^RA[0-9]{13}$/

let nextRowId = 1
const newRow = (code = '') => ({ id: nextRowId++, code })

// Same rules as the backend (ApplicationRequest + CourseListValidator), checked while typing
// so the student sees mistakes before submitting.
function validate(form, rows) {
  const errors = {}
  if (!REGISTER_NO.test(form.registerNo)) errors.registerNo = 'Register number must look like RA2411026011139.'
  if (!form.studentName.trim()) errors.studentName = 'Enter your name.'
  if (!EMAIL.test(form.studentEmail)) errors.studentEmail = 'Enter a valid email address.'
  if (rows.length === 0) errors.courses = 'Add at least one course.'
  const seen = new Set()
  rows.forEach((r) => {
    if (!COURSE_PATTERN.test(r.code)) errors[`row${r.id}`] = 'Course code must look like 21CSC305P.'
    else if (seen.has(r.code)) errors[`row${r.id}`] = 'This course is already in the list.'
    seen.add(r.code)
  })
  return errors
}

export default function ApplyPage() {
  const toast = useToast()
  const [form, setForm] = useState({ registerNo: '', studentName: '', studentEmail: '', paymentWindow: '', examinerDeadline: '' })
  const [rows, setRows] = useState([newRow('21CSC305P')])
  const [touched, setTouched] = useState(false)
  const [serverErrors, setServerErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)
  const [created, setCreated] = useState(null)

  const errors = { ...validate(form, rows), ...serverErrors }
  const valid = Object.keys(validate(form, rows)).length === 0
  const show = (key) => (touched || form[key]) && errors[key]

  const setField = (key, value) => {
    setForm((f) => ({ ...f, [key]: value }))
    setServerErrors({})
  }
  const setRowCode = (id, code) => {
    setRows((rs) => rs.map((r) => (r.id === id ? { ...r, code: code.toUpperCase().trim() } : r)))
    setServerErrors({})
  }

  async function submit(e) {
    e.preventDefault()
    setTouched(true)
    if (!valid) return
    setSubmitting(true)
    try {
      const result = await api.submitApplication({
        registerNo: form.registerNo,
        studentName: form.studentName,
        studentEmail: form.studentEmail,
        courses: rows.map((r) => r.code),
        paymentWindow: form.paymentWindow || null,
        examinerDeadline: form.examinerDeadline || null,
      })
      setCreated(result)
      toast('Application submitted')
    } catch (err) {
      setServerErrors(err.fieldErrors || {})
      toast(err.message, 'error')
    } finally {
      setSubmitting(false)
    }
  }

  if (created) {
    return (
      <div className="card mx-auto max-w-xl text-center">
        <p className="text-sm text-muted">Application submitted</p>
        <h2 className="mt-1 text-2xl font-semibold">{created.applicationId}</h2>
        <p className="mt-3 text-sm text-muted">
          One revaluation was started for each course (process instance {created.processInstanceKey}).
          A fee payment link for each eligible course is being created now.
        </p>
        <div className="mt-5 flex justify-center gap-3">
          <Link className="btn" to={`/applications/${created.applicationId}`}>Track status</Link>
          <button className="btn-light" onClick={() => { setCreated(null); setRows([newRow()]); setTouched(false) }}>New application</button>
        </div>
      </div>
    )
  }

  return (
    <form onSubmit={submit} noValidate className="grid gap-6 lg:grid-cols-[1fr_320px]">
      <div className="card space-y-5">
        <div>
          <h2 className="text-xl font-semibold">Apply for revaluation</h2>
          <p className="text-sm text-muted">Your published marks are fetched from the results system. Fee: ₹1,000 per eligible course, refunded if your marks increase.</p>
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <label className="block text-sm">
            <span className="font-medium">Register number</span>
            <input className={`field mt-1 ${show('registerNo') ? 'field-error' : ''}`} value={form.registerNo}
              onChange={(e) => setField('registerNo', e.target.value.toUpperCase().trim())} placeholder="RA2411026011139" />
            {show('registerNo') && <span className="mt-1 block text-xs text-red-700">{errors.registerNo}</span>}
          </label>
          <label className="block text-sm">
            <span className="font-medium">Name</span>
            <input className={`field mt-1 ${show('studentName') ? 'field-error' : ''}`} value={form.studentName}
              onChange={(e) => setField('studentName', e.target.value)} placeholder="Ram Prasath N" />
            {show('studentName') && <span className="mt-1 block text-xs text-red-700">{errors.studentName}</span>}
          </label>
          <label className="block text-sm sm:col-span-2">
            <span className="font-medium">Email</span>
            <input type="email" className={`field mt-1 ${show('studentEmail') ? 'field-error' : ''}`} value={form.studentEmail}
              onChange={(e) => setField('studentEmail', e.target.value.trim())} placeholder="ram@example.com" />
            {show('studentEmail') && <span className="mt-1 block text-xs text-red-700">{errors.studentEmail}</span>}
          </label>
        </div>

        {/* Dynamic array item builder: this list becomes the process variable "courses" */}
        <fieldset>
          <legend className="text-sm font-medium">Courses</legend>
          <p className="mb-2 text-xs text-muted">
            Each course you add becomes one parallel instance of the <b>Revaluate course</b> call activity
            (multi-instance over the <code>courses</code> list), so every paper is revalued independently.
          </p>
          <ul className="space-y-2">
            {rows.map((row, i) => (
              <li key={row.id} className="flex items-start gap-2">
                <span className="mt-2 w-6 text-right text-xs text-muted">{i + 1}.</span>
                <div className="flex-1">
                  <input list="test-courses" aria-label={`Course ${i + 1}`} value={row.code}
                    className={`field ${(touched || row.code) && errors[`row${row.id}`] ? 'field-error' : ''}`}
                    onChange={(e) => setRowCode(row.id, e.target.value)} placeholder="21CSC305P" />
                  {(touched || row.code) && errors[`row${row.id}`] && (
                    <span className="mt-1 block text-xs text-red-700">{errors[`row${row.id}`]}</span>
                  )}
                </div>
                <button type="button" className="btn-light mt-0.5" aria-label={`Remove course ${i + 1}`}
                  onClick={() => setRows((rs) => rs.filter((r) => r.id !== row.id))}>Remove</button>
              </li>
            ))}
          </ul>
          <datalist id="test-courses">
            {TEST_COURSES.map((c) => <option key={c.code} value={c.code}>{c.name}</option>)}
          </datalist>
          {(touched && errors.courses) && <p className="mt-1 text-xs text-red-700">{errors.courses}</p>}
          <button type="button" className="btn-light mt-3" disabled={rows.length >= 6}
            onClick={() => setRows((rs) => [...rs, newRow()])}>+ Add course</button>
          <span className="ml-2 text-xs text-muted">{rows.length} of 6</span>
        </fieldset>

        <details className="rounded-md border border-line p-3 text-sm">
          <summary className="cursor-pointer font-medium">Demo options (short timers)</summary>
          <p className="mt-2 text-xs text-muted">Production values are 48 hours to pay and 72 hours for the examiner. Pick a short timer to show the timer events in a demo.</p>
          <div className="mt-2 grid gap-3 sm:grid-cols-2">
            <label>Payment window
              <select className="field mt-1" value={form.paymentWindow} onChange={(e) => setField('paymentWindow', e.target.value)}>
                <option value="">48 hours (default)</option><option value="PT1M">1 minute</option><option value="PT2M">2 minutes</option>
              </select>
            </label>
            <label>Examiner deadline
              <select className="field mt-1" value={form.examinerDeadline} onChange={(e) => setField('examinerDeadline', e.target.value)}>
                <option value="">72 hours (default)</option><option value="PT1M">1 minute</option><option value="PT2M">2 minutes</option>
              </select>
            </label>
          </div>
        </details>

        <button type="submit" className="btn w-full sm:w-auto" disabled={submitting || (touched && !valid)}>
          {submitting ? 'Submitting…' : `Submit application (${rows.length} course${rows.length === 1 ? '' : 's'})`}
        </button>
      </div>

      <aside className="card h-fit text-sm">
        <h3 className="font-semibold">Test courses</h3>
        <p className="mb-3 text-xs text-muted">Known to the demo results system. Any other code ends as “result not found”.</p>
        <ul className="space-y-2">
          {TEST_COURSES.map((c) => (
            <li key={c.code}>
              <button type="button" className="text-left hover:text-brand"
                onClick={() => !rows.some((r) => r.code === c.code) && setRows((rs) => [...rs.filter((r) => r.code), newRow(c.code)])}>
                <span className="font-mono font-medium">{c.code}</span> {c.name}
                <span className="block text-xs text-muted">{c.note}</span>
              </button>
            </li>
          ))}
        </ul>
      </aside>
    </form>
  )
}
