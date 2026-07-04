const healthColors = {
  HEALTHY: 'bg-emerald-900 text-emerald-200',
  PAUSED: 'bg-slate-700 text-slate-200',
  BACKLOG: 'bg-amber-900 text-amber-200',
  AT_CAPACITY: 'bg-orange-900 text-orange-200',
};

export default function QueueList({ queues, onPause, onResume }) {
  if (!queues?.length) {
    return <p className="text-slate-400 text-sm">No queues yet. Create one to start scheduling jobs.</p>;
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="text-left text-slate-400 border-b border-slate-700">
            <th className="py-2 pr-4">Name</th>
            <th className="py-2 pr-4">Health</th>
            <th className="py-2 pr-4">Running</th>
            <th className="py-2 pr-4">Queued</th>
            <th className="py-2 pr-4">Failed/DLQ</th>
            <th className="py-2 pr-4">Limit</th>
            <th className="py-2">Actions</th>
          </tr>
        </thead>
        <tbody>
          {queues.map((q) => {
            const queued = (q.statusCounts?.QUEUED || 0) + (q.statusCounts?.SCHEDULED || 0);
            const failed = (q.statusCounts?.FAILED || 0) + (q.statusCounts?.DEAD_LETTER || 0);
            return (
              <tr key={q.id} className="border-b border-slate-800">
                <td className="py-2 pr-4 font-medium">{q.name}</td>
                <td className="py-2 pr-4">
                  <span className={`px-2 py-0.5 rounded text-xs ${healthColors[q.health] || healthColors.HEALTHY}`}>
                    {q.health}
                  </span>
                </td>
                <td className="py-2 pr-4">{q.runningCount ?? 0}</td>
                <td className="py-2 pr-4">{queued}</td>
                <td className="py-2 pr-4">{failed}</td>
                <td className="py-2 pr-4">{q.concurrencyLimit}</td>
                <td className="py-2">
                  {q.paused ? (
                    <button onClick={() => onResume(q.id)} className="text-emerald-400 hover:underline text-xs">
                      Resume
                    </button>
                  ) : (
                    <button onClick={() => onPause(q.id)} className="text-amber-400 hover:underline text-xs">
                      Pause
                    </button>
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
