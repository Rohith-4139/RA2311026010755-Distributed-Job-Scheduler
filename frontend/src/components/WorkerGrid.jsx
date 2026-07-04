const statusStyles = {
  ACTIVE: 'border-emerald-700 bg-emerald-950/40',
  DEAD: 'border-red-800 bg-red-950/30',
  INACTIVE: 'border-slate-600 bg-slate-900',
};

export default function WorkerGrid({ workers }) {
  if (!workers?.length) {
    return <p className="text-slate-400 text-sm">No workers registered yet.</p>;
  }

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
      {workers.map((w) => (
        <div key={w.id} className={`border rounded-lg p-4 ${statusStyles[w.status] || statusStyles.INACTIVE}`}>
          <div className="flex justify-between items-start">
            <div>
              <p className="font-medium">{w.name}</p>
              <p className="text-xs text-slate-400 truncate max-w-[180px]" title={w.id}>{w.id}</p>
            </div>
            <span className="text-xs uppercase tracking-wide">{w.status}</span>
          </div>
          <div className="mt-3 text-xs space-y-1 text-slate-300">
            <p>Active jobs: {w.activeExecutions}</p>
            <p>Last ping: {w.lastPing ? w.lastPing.replace('T', ' ').slice(0, 19) : '—'}</p>
          </div>
        </div>
      ))}
    </div>
  );
}
