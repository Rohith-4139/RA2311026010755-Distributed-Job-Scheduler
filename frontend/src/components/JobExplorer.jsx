const statusColors = {
  QUEUED: 'text-blue-300',
  SCHEDULED: 'text-cyan-300',
  CLAIMED: 'text-violet-300',
  RUNNING: 'text-yellow-300',
  COMPLETED: 'text-emerald-300',
  FAILED: 'text-red-300',
  DEAD_LETTER: 'text-orange-300',
};

export default function JobExplorer({ jobsPage, filters, onFilterChange, onPageChange, queues }) {
  const jobs = jobsPage?.content || [];
  const totalPages = jobsPage?.totalPages || 0;

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
        <select
          className="bg-slate-800 border border-slate-600 rounded px-2 py-1.5 text-sm"
          value={filters.queueId}
          onChange={(e) => onFilterChange({ ...filters, queueId: e.target.value, page: 0 })}
        >
          <option value="">All queues</option>
          {queues?.map((q) => (
            <option key={q.id} value={q.id}>{q.name}</option>
          ))}
        </select>

        <select
          className="bg-slate-800 border border-slate-600 rounded px-2 py-1.5 text-sm"
          value={filters.status}
          onChange={(e) => onFilterChange({ ...filters, status: e.target.value, page: 0 })}
        >
          <option value="">All statuses</option>
          {['QUEUED', 'SCHEDULED', 'RUNNING', 'COMPLETED', 'FAILED', 'DEAD_LETTER'].map((s) => (
            <option key={s} value={s}>{s}</option>
          ))}
        </select>

        <input
          type="datetime-local"
          className="bg-slate-800 border border-slate-600 rounded px-2 py-1.5 text-sm"
          value={filters.start}
          onChange={(e) => onFilterChange({ ...filters, start: e.target.value, page: 0 })}
        />

        <input
          type="datetime-local"
          className="bg-slate-800 border border-slate-600 rounded px-2 py-1.5 text-sm"
          value={filters.end}
          onChange={(e) => onFilterChange({ ...filters, end: e.target.value, page: 0 })}
        />
      </div>

      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-slate-400 border-b border-slate-700">
              <th className="py-2 pr-3">ID</th>
              <th className="py-2 pr-3">Queue</th>
              <th className="py-2 pr-3">Status</th>
              <th className="py-2 pr-3">Payload</th>
              <th className="py-2 pr-3">Retries</th>
              <th className="py-2 pr-3">Created</th>
            </tr>
          </thead>
          <tbody>
            {jobs.map((job) => (
              <tr key={job.id} className="border-b border-slate-800">
                <td className="py-2 pr-3">{job.id}</td>
                <td className="py-2 pr-3">{job.queue?.name || job.queue?.id}</td>
                <td className={`py-2 pr-3 font-medium ${statusColors[job.status] || ''}`}>{job.status}</td>
                <td className="py-2 pr-3 max-w-xs truncate" title={job.payload}>{job.payload}</td>
                <td className="py-2 pr-3">{job.retryCount}/{job.maxRetries}</td>
                <td className="py-2 pr-3">{job.createdAt?.replace('T', ' ').slice(0, 19)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {jobs.length === 0 && <p className="text-slate-400 text-sm">No jobs match the current filters.</p>}

      <div className="flex items-center gap-3 text-sm">
        <button
          disabled={filters.page <= 0}
          onClick={() => onPageChange(filters.page - 1)}
          className="px-3 py-1 rounded bg-slate-800 disabled:opacity-40"
        >
          Prev
        </button>
        <span>Page {filters.page + 1} / {Math.max(totalPages, 1)}</span>
        <button
          disabled={filters.page + 1 >= totalPages}
          onClick={() => onPageChange(filters.page + 1)}
          className="px-3 py-1 rounded bg-slate-800 disabled:opacity-40"
        >
          Next
        </button>
      </div>
    </div>
  );
}
