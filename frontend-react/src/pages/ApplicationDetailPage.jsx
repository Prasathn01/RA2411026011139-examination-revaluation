import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { api } from '../api.js'
import StatusBadge from '../components/StatusBadge.jsx'
import ProcessDiagram from '../components/ProcessDiagram.jsx'
import { useToast } from '../components/Toast.jsx'

// The order of the steps on each course card (labels match the stage names sent by the backend).
const STEPS = ['Fetching result', 'Checking eligibility', 'Awaiting payment', 'Assigning examiner', 'Under revaluation',
  'Deciding outcome', 'Publishing result', 'Generating letter', 'Notifying student', 'Completed']
const STEP_OF = {
  'Creating payment link': 'Awaiting payment',
  'Waiting for CoE to assign examiner': 'Assigning examiner',
  'Examiner assigned': 'Assigning examiner',
  'Waiting for CoE to assign third examiner': 'Under revaluation',
  'Third valuation': 'Under revaluation',
  'Waiting for CoE to update result': 'Publishing result',
  'Refunding fee': 'Publishing result',
}

const POLL_MS = 4000

/**
 * Live status tracker. Polls the backend every 4 seconds; the backend asks Camunda which element
 * each course-revaluation instance is waiting in. (Polling keeps it simple; WebSocket or
 * Server-Sent Events would push changes instead and are listed as a future improvement.)
 */
export default function ApplicationDetailPage() {
  const { applicationId } = useParams()
  const toast = useToast()
  const [app, setApp] = useState(null)
  const [timeline, setTimeline] = useState([])
  const [error, setError] = useState(null)
  const [paying, setPaying] = useState(null)
  const [diagramFor, setDiagramFor] = useState(null)

  const load = useCallback(() => {
    api.getApplication(applicationId).then((a) => { setApp(a); setError(null) }).catch((e) => setError(e.message))
    api.getTimeline(applicationId).then(setTimeline).catch(() => {})
  }, [applicationId])

  useEffect(() => {
    load()
    const timer = setInterval(load, POLL_MS)
    return () => clearInterval(timer)
  }, [load])

  async function pay(requestId) {
    setPaying(requestId)
    try {
      const res = await api.simulatePayment(requestId)
      toast(res.message)
    } catch (e) {
      toast(e.message, 'error')
    } finally {
      setPaying(null)
    }
  }

  if (error && !app) return <p className="card text-red-700">{error}</p>
  if (!app) return <p className="text-muted">Loading…</p>

  const shown = app.courses.find((c) => c.courseCode === diagramFor)

  return (
    <div className="space-y-6">
      <div className="card flex flex-wrap items-start justify-between gap-4">
        <div>
          <Link to="/applications" className="text-xs text-brand hover:underline">← All applications</Link>
          <h2 className="mt-1 text-xl font-semibold">{app.applicationId}</h2>
          <p className="text-sm text-muted">{app.studentName} · {app.registerNo} · {app.studentEmail}</p>
        </div>
        <div className="text-right text-sm">
          <StatusBadge status={app.status} />
          <p className="mt-1 text-xs text-muted">Process instance {app.processInstanceKey ?? '—'} ({app.parentState ?? 'not started'})</p>
          {app.totalRefund != null && <p className="mt-1 text-xs">Total refunded: ₹{app.totalRefund}</p>}
          <p className="mt-1 text-xs text-muted">Refreshes every {POLL_MS / 1000} s</p>
        </div>
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        {app.courses.map((c) => <CourseCard key={c.courseCode} course={c} paying={paying === c.requestId}
          onPay={() => pay(c.requestId)} onDiagram={() => setDiagramFor(diagramFor === c.courseCode ? null : c.courseCode)}
          diagramOpen={diagramFor === c.courseCode} />)}
      </div>

      {shown && shown.processInstanceKey && (
        <div className="card">
          <h3 className="mb-2 font-semibold">Where is {shown.courseCode} right now?</h3>
          <p className="mb-3 text-xs text-muted">course-revaluation.bpmn — the highlighted element is the one Camunda is waiting in.</p>
          <ProcessDiagram processId="course-revaluation" activeElements={shown.activeElements} />
        </div>
      )}

      <div className="card">
        <h3 className="mb-3 font-semibold">Timeline</h3>
        <ol className="max-h-[28rem] space-y-1 overflow-y-auto text-sm">
          {timeline.length === 0 && <li className="text-muted">Nothing yet.</li>}
          {timeline.map((t, i) => (
            <li key={i} className="flex gap-3 border-b border-line/60 py-1 last:border-0">
              <span className="w-20 shrink-0 font-mono text-xs text-muted">{new Date(t.time).toLocaleTimeString()}</span>
              <span className={`w-28 shrink-0 text-xs font-semibold ${t.source === 'CAMUNDA' ? 'text-brand' : 'text-amber-700'}`}>
                {t.source === 'CAMUNDA' ? (t.courseCode || 'Application') : t.source.replace('_', ' ')}
              </span>
              <span className="min-w-0 flex-1 break-words">{t.label}{t.state === 'ACTIVE' && <span className="ml-2 rounded bg-blue-100 px-1.5 text-xs text-blue-800">active</span>}</span>
            </li>
          ))}
        </ol>
      </div>
    </div>
  )
}

function CourseCard({ course: c, paying, onPay, onDiagram, diagramOpen }) {
  const step = STEPS.indexOf(STEP_OF[c.stage] || c.stage)
  const done = c.stage === 'Completed'
  return (
    <div className="card space-y-3">
      <div className="flex items-start justify-between gap-2">
        <div>
          <h3 className="font-semibold"><span className="font-mono">{c.courseCode}</span> {c.courseName}</h3>
          <p className="font-mono text-xs text-muted">{c.requestId || 'request ID not created yet'}</p>
        </div>
        <StatusBadge status={c.status} />
      </div>

      <div>
        <p className="text-sm">
          <span className="text-muted">Stage: </span><b>{c.stage}</b>
          {c.incident && <span className="ml-2 rounded bg-red-100 px-1.5 py-0.5 text-xs font-semibold text-red-800">incident – CoE notified</span>}
          {c.escalated && <span className="ml-2 rounded bg-amber-100 px-1.5 py-0.5 text-xs font-semibold text-amber-800">escalated to CoE</span>}
        </p>
        {!done || c.status === 'CHANGED' || c.status === 'UNCHANGED' ? (
          <div className="mt-2 flex gap-1" aria-label="progress">
            {STEPS.map((s, i) => (
              <div key={s} title={s} className={`h-1.5 flex-1 rounded ${i < step || done ? 'bg-emerald-500' : i === step ? 'bg-brand animate-pulse' : 'bg-line'}`} />
            ))}
          </div>
        ) : null}
      </div>

      <dl className="grid grid-cols-2 gap-x-3 gap-y-1 text-sm">
        {c.originalMarks != null && <><dt className="text-muted">Published marks</dt><dd>{c.originalMarks}</dd></>}
        {c.examinerName && <><dt className="text-muted">Examiner</dt><dd>{c.examinerName}</dd></>}
        {c.finalMarks != null && <><dt className="text-muted">Final marks</dt><dd className="font-semibold">{c.finalMarks} ({c.grade})</dd></>}
        {c.refundAmount != null && done && <><dt className="text-muted">Fee refund</dt><dd>{Number(c.refundAmount) > 0 ? `₹${c.refundAmount}` : 'None'}</dd></>}
      </dl>
      {c.reason && done && c.status !== 'CHANGED' && c.status !== 'UNCHANGED' && <p className="rounded bg-paper p-2 text-sm">{c.reason}</p>}

      <div className="flex flex-wrap gap-2">
        {c.paymentPending && (
          <>
            <button className="btn" disabled={paying} onClick={onPay}>{paying ? 'Paying…' : 'Pay fee ₹1,000 (demo)'}</button>
            {c.paymentUrl && <a className="btn-light" href={c.paymentUrl} target="_blank" rel="noreferrer">Open payment link</a>}
          </>
        )}
        {c.letterUrl && <a className="btn-light" href={c.letterUrl} target="_blank" rel="noreferrer">Outcome letter</a>}
        {c.processInstanceKey && <button className="btn-light" onClick={onDiagram}>{diagramOpen ? 'Hide diagram' : 'Show in diagram'}</button>}
      </div>
    </div>
  )
}
