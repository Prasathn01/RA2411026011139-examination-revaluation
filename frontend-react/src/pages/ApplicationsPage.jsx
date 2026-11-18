import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { api } from '../api.js'
import StatusBadge from '../components/StatusBadge.jsx'

// List of applications with a small count-by-status dashboard on top.
export default function ApplicationsPage() {
  const [apps, setApps] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    const load = () => api.listApplications().then(setApps).catch((e) => setError(e.message))
    load()
    const timer = setInterval(load, 5000)
    return () => clearInterval(timer)
  }, [])

  if (error) return <p className="card text-red-700">{error}</p>
  if (!apps) return <p className="text-muted">Loading…</p>

  const counts = apps.reduce((acc, a) => ({ ...acc, [a.status]: (acc[a.status] || 0) + 1 }), {})

  return (
    <div className="space-y-5">
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        {[['Total', apps.length], ['In progress', counts.IN_PROGRESS || 0], ['Completed', counts.COMPLETED || 0], ['Failed to start', counts.FAILED_TO_START || 0]]
          .map(([label, n]) => (
            <div key={label} className="card py-4">
              <p className="text-xs text-muted">{label}</p>
              <p className="text-2xl font-semibold">{n}</p>
            </div>
          ))}
      </div>

      <div className="card overflow-x-auto p-0">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-line bg-paper text-xs uppercase text-muted">
            <tr><th className="px-4 py-3">Application</th><th className="px-4 py-3">Student</th><th className="px-4 py-3">Courses</th><th className="px-4 py-3">Status</th><th className="px-4 py-3">Submitted</th></tr>
          </thead>
          <tbody>
            {apps.length === 0 && <tr><td colSpan="5" className="px-4 py-6 text-center text-muted">No applications yet. <Link className="text-brand underline" to="/">Apply</Link></td></tr>}
            {apps.map((a) => (
              <tr key={a.applicationId} className="border-b border-line last:border-0 hover:bg-paper/60">
                <td className="px-4 py-3"><Link className="font-medium text-brand hover:underline" to={`/applications/${a.applicationId}`}>{a.applicationId}</Link></td>
                <td className="px-4 py-3">{a.studentName}<span className="block text-xs text-muted">{a.registerNo}</span></td>
                <td className="px-4 py-3 font-mono text-xs">{a.courses.join(', ')}</td>
                <td className="px-4 py-3"><StatusBadge status={a.status} /></td>
                <td className="px-4 py-3 text-xs text-muted">{new Date(a.createdAt).toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
