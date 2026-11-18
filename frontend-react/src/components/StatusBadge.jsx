// Coloured pill for application and course statuses.
const COLOURS = {
  CHANGED: 'bg-emerald-100 text-emerald-800',
  UNCHANGED: 'bg-slate-100 text-slate-700',
  REJECTED: 'bg-red-100 text-red-800',
  CANCELLED: 'bg-amber-100 text-amber-800',
  COMPLETED: 'bg-emerald-100 text-emerald-800',
  IN_PROGRESS: 'bg-blue-100 text-blue-800',
  SUBMITTED: 'bg-blue-100 text-blue-800',
  FAILED_TO_START: 'bg-red-100 text-red-800',
}

const LABELS = { IN_PROGRESS: 'In progress', FAILED_TO_START: 'Failed to start' }

export default function StatusBadge({ status }) {
  if (!status) return null
  return (
    <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-semibold ${COLOURS[status] || 'bg-slate-100 text-slate-700'}`}>
      {LABELS[status] || status.charAt(0) + status.slice(1).toLowerCase().replaceAll('_', ' ')}
    </span>
  )
}
