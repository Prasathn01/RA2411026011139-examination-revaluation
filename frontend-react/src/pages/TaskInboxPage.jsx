import { useCallback, useEffect, useState } from 'react'
import { Navigate } from 'react-router'
import { api } from '../api.js'
import { useAuth } from '../auth.jsx'
import { useToast } from '../components/Toast.jsx'

// Staff inbox. The backend decides what each user sees: the CoE gets the "coe" tasks
// (assign examiner, assign third examiner, delays, manual result updates); an examiner gets
// only the scripts assigned to them. Tasks are already assigned, so there is nothing to claim.
export default function TaskInboxPage() {
  const { user, ready } = useAuth()
  const toast = useToast()
  const [tasks, setTasks] = useState([])
  const [selected, setSelected] = useState(null)   // {task, variables}
  const [error, setError] = useState(null)

  const load = useCallback(() => {
    api.listTasks().then((t) => { setTasks(t); setError(null) }).catch((e) => setError(e.message))
  }, [])

  useEffect(() => {
    if (!user) return undefined
    load()
    const timer = setInterval(load, 5000)
    return () => clearInterval(timer)
  }, [load, user])

  if (!ready) return <div className="card text-sm text-muted">Checking sign-in…</div>
  if (!user) return <Navigate to="/login" replace />

  async function open(taskKey) {
    try {
      setSelected(await api.getTask(taskKey))
    } catch (e) {
      toast(e.message, 'error')
      load()
    }
  }

  const coe = user.role === 'COE'
  return (
    <div className="grid gap-6 lg:grid-cols-[380px_1fr]">
      <div className="space-y-4">
        <div className="card">
          <p className="text-sm">Signed in as <span className="font-semibold">{user.displayName}</span> <span className="font-mono text-xs text-muted">({user.username})</span></p>
          <p className="mt-1 text-xs text-muted">{coe
            ? 'Controller of Examinations: assign examiners, handle delays and manual result updates.'
            : 'Examiner: only the scripts the CoE assigned to you are listed.'}</p>
        </div>

        <div className="card p-0">
          <div className="flex items-center justify-between border-b border-line px-4 py-3">
            <h2 className="font-semibold">Open tasks ({tasks.length})</h2>
            <button className="btn-light" onClick={load}>Refresh</button>
          </div>
          {error && <p className="p-4 text-sm text-red-700">{error}</p>}
          <ul>
            {tasks.length === 0 && !error && <li className="p-4 text-sm text-muted">No open tasks.</li>}
            {tasks.map((t) => (
              <li key={t.taskKey}>
                <button onClick={() => open(t.taskKey)}
                  className={`w-full border-b border-line px-4 py-3 text-left last:border-0 hover:bg-paper ${selected?.task.taskKey === t.taskKey ? 'bg-paper' : ''}`}>
                  <p className="text-sm font-medium">{t.name}</p>
                  <p className="font-mono text-xs text-muted">{t.courseCode} · {t.requestId}</p>
                  <p className="mt-1 flex flex-wrap gap-2 text-xs">
                    <span className="text-muted">{new Date(t.createdAt).toLocaleString()}</span>
                    {t.escalated && <span className="rounded bg-amber-100 px-1.5 font-semibold text-amber-800">overdue – escalated to CoE</span>}
                  </p>
                </button>
              </li>
            ))}
          </ul>
        </div>
      </div>

      <div>
        {!selected && <div className="card text-sm text-muted">Select a task to open its form.</div>}
        {selected && (
          <TaskForm key={selected.task.taskKey} detail={selected} onDone={() => { setSelected(null); load() }} />
        )}
      </div>
    </div>
  )
}

const ASSIGN_TASKS = ['Task_CoeAssign', 'Task_CoeAssignThird']

function TaskForm({ detail, onDone }) {
  const toast = useToast()
  const { task, variables: v } = detail
  const options = v.examinerOptions || []
  const [values, setValues] = useState({})
  const [external, setExternal] = useState(options.length === 0)   // no one in the directory: add an external examiner
  const [errors, setErrors] = useState({})
  const [saving, setSaving] = useState(false)
  const set = (k, val) => { setValues((x) => ({ ...x, [k]: val })); setErrors({}) }

  async function complete(e) {
    e.preventDefault()
    const body = ASSIGN_TASKS.includes(task.elementId)
      ? (external ? { examinerName: values.examinerName, examinerEmail: values.examinerEmail } : { selectedExaminerId: values.selectedExaminerId })
      : values
    const local = check(task.elementId, body, external)
    if (Object.keys(local).length) { setErrors(local); return }
    setSaving(true)
    try {
      await api.completeTask(task.taskKey, body)
      toast(ASSIGN_TASKS.includes(task.elementId) ? 'Examiner assigned; the login email is on its way' : `${task.name} completed`)
      onDone()
    } catch (err) {
      setErrors(err.fieldErrors || {})
      toast(err.message, 'error')
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={complete} noValidate className="card space-y-4">
      <div>
        <h2 className="text-lg font-semibold">{task.name}</h2>
        <p className="font-mono text-xs text-muted">{v.requestId}</p>
      </div>

      <dl className="grid grid-cols-[140px_1fr] gap-y-1 rounded-md bg-paper p-3 text-sm">
        <dt className="text-muted">Course</dt><dd>{v.courseCode} – {v.courseName}</dd>
        {v.registerNo && <><dt className="text-muted">Register no.</dt><dd>{v.registerNo}</dd></>}
        {v.originalExaminerId && <><dt className="text-muted">First valued by</dt><dd>{v.originalExaminerId} (cannot be picked)</dd></>}
        {v.examinerName && <><dt className="text-muted">{task.elementId === 'Task_CoeAssignThird' ? 'Revalued by' : 'Examiner'}</dt>
          <dd>{v.examinerName}{task.elementId === 'Task_CoeAssignThird' && ' (cannot be picked)'}</dd></>}
        {v.finalMarks != null && <><dt className="text-muted">Final marks</dt><dd>{v.finalMarks} ({v.grade})</dd></>}
      </dl>

      <fieldset disabled={saving} className="space-y-3 disabled:opacity-60">
        {ASSIGN_TASKS.includes(task.elementId) && <>
          <p className="text-xs text-muted">{task.elementId === 'Task_CoeAssignThird'
            ? 'The revaluation differed from the published marks by more than 15. Pick a new examiner for an independent third valuation.'
            : 'Pick the examiner who will revalue this script.'} The system then creates the examiner&apos;s portal login and emails the username and a temporary password.</p>
          {!external && <>
            <div className="space-y-2">
              {options.map((o) => (
                <label key={o.examinerId} className={`flex cursor-pointer items-center gap-3 rounded-md border px-3 py-2 text-sm ${values.selectedExaminerId === o.examinerId ? 'border-navy bg-paper' : 'border-line'}`}>
                  <input type="radio" name="examiner" value={o.examinerId} checked={values.selectedExaminerId === o.examinerId}
                    onChange={() => set('selectedExaminerId', o.examinerId)} />
                  <span className="flex-1"><span className="font-medium">{o.name}</span> <span className="font-mono text-xs text-muted">{o.examinerId}</span>
                    <span className="block text-xs text-muted">{o.department} · {o.activeAssignments} open assignment{o.activeAssignments === 1 ? '' : 's'}</span></span>
                </label>
              ))}
            </div>
            {errors.selectedExaminerId && <p className="text-xs text-red-700">{errors.selectedExaminerId}</p>}
            <button type="button" className="text-sm text-navy underline" onClick={() => { setExternal(true); setErrors({}) }}>Add an external examiner instead</button>
          </>}
          {external && <>
            {options.length === 0 && <p className="rounded-md bg-amber-50 p-2 text-sm text-amber-900">Nobody in the examiner directory may value this course (the only examiner did the first valuation). Add an external examiner.</p>}
            <Text label="External examiner name" name="examinerName" values={values} errors={errors} set={set} />
            <Text label="External examiner email" name="examinerEmail" values={values} errors={errors} set={set} />
            {options.length > 0 && <button type="button" className="text-sm text-navy underline" onClick={() => { setExternal(false); setErrors({}) }}>Back to the examiner list</button>}
          </>}
        </>}
        {task.elementId === 'Task_Revalue' && <>
          <p className="text-xs text-muted">Blind revaluation: the original marks are deliberately not shown.</p>
          <Num label="Revalued marks (out of 100)" name="revaluedMarks" values={values} errors={errors} set={set} />
          <Text label="Remarks" name="examinerRemarks" values={values} set={set} area />
        </>}
        {task.elementId === 'Task_ThirdValuation' && <>
          <p className="text-xs text-muted">The first revaluation differed from the published marks by more than 15. Value the script independently; this mark is final.</p>
          <Num label="Third valuation marks (out of 100)" name="thirdValuationMarks" values={values} errors={errors} set={set} />
          <Text label="Remarks" name="thirdValuationRemarks" values={values} set={set} area />
        </>}
        {(task.elementId === 'Task_ManualUpdate' || task.elementId === 'Task_Escalate') && <>
          <p className="text-xs text-muted">{task.elementId === 'Task_Escalate'
            ? 'The examiner has not finished within the deadline. Record what was done (the examiner task stays open).'
            : 'The results system rejected the update. Enter the result by hand, then record it here.'}</p>
          <Text label="Action taken" name="actionTaken" values={values} errors={errors} set={set} area />
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={!!values.actionCompleted} onChange={(e) => set('actionCompleted', e.target.checked)} /> Done
          </label>
          {errors.actionCompleted && <p className="text-xs text-red-700">{errors.actionCompleted}</p>}
        </>}
      </fieldset>

      <button type="submit" className="btn" disabled={saving}>{saving ? 'Saving…' : ASSIGN_TASKS.includes(task.elementId) ? 'Assign and email login' : 'Complete task'}</button>
    </form>
  )
}

// Client-side checks mirror TaskService on the backend.
function check(elementId, v, external) {
  const e = {}
  const mark = (k) => {
    const n = Number(v[k])
    if (v[k] === undefined || v[k] === '' || !Number.isInteger(n) || n < 0 || n > 100) e[k] = 'Enter a whole number from 0 to 100.'
  }
  if (elementId === 'Task_Revalue') mark('revaluedMarks')
  if (elementId === 'Task_ThirdValuation') mark('thirdValuationMarks')
  if (ASSIGN_TASKS.includes(elementId)) {
    if (!external && !v.selectedExaminerId) e.selectedExaminerId = 'Pick an examiner.'
    if (external && !v.examinerName?.trim()) e.examinerName = 'Enter the examiner name.'
    if (external && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v.examinerEmail || '')) e.examinerEmail = 'Enter a valid email.'
  }
  if (elementId === 'Task_ManualUpdate' || elementId === 'Task_Escalate') {
    if (!v.actionTaken?.trim()) e.actionTaken = 'Describe the action taken.'
    if (!v.actionCompleted) e.actionCompleted = 'Tick Done to confirm.'
  }
  return e
}

function Num({ label, name, values, errors, set }) {
  return (
    <label className="block text-sm font-medium">{label}
      <input type="number" min="0" max="100" className={`field mt-1 block max-w-40 ${errors[name] ? 'field-error' : ''}`}
        value={values[name] ?? ''} onChange={(e) => set(name, e.target.value === '' ? '' : Number(e.target.value))} />
      {errors[name] && <span className="mt-1 block text-xs font-normal text-red-700">{errors[name]}</span>}
    </label>
  )
}

function Text({ label, name, values, errors = {}, set, area }) {
  const Tag = area ? 'textarea' : 'input'
  return (
    <label className="block text-sm font-medium">{label}
      <Tag className={`field mt-1 ${errors[name] ? 'field-error' : ''}`} rows={area ? 3 : undefined}
        value={values[name] ?? ''} onChange={(e) => set(name, e.target.value)} />
      {errors[name] && <span className="mt-1 block text-xs font-normal text-red-700">{errors[name]}</span>}
    </label>
  )
}
