import { useState } from 'react';
import { api } from '../api';

export default function DlqView({ dlqPage, onRefresh }) {
  const [retrying, setRetrying] = useState(null);
  // Deduplicate entries by job id (keep the most recent failedAt per job)
  const rawEntries = dlqPage?.content || [];
  const entries = (() => {
    const map = new Map();
    for (const e of rawEntries) {
      const jobId = e.job?.id ?? null;
      if (jobId == null) {
        // keep entries without job reference as-is (use their own id)
        map.set(`dlq-${e.id}`, e);
        continue;
      }
      const existing = map.get(jobId);
      if (!existing) {
        map.set(jobId, e);
      } else {
        const existingTime = existing.failedAt ? new Date(existing.failedAt).getTime() : 0;
        const candidateTime = e.failedAt ? new Date(e.failedAt).getTime() : 0;
        if (candidateTime > existingTime) map.set(jobId, e);
      }
    }
    // return sorted by failedAt desc
    return Array.from(map.values()).sort((a, b) => new Date(b.failedAt) - new Date(a.failedAt));
  })();

  const handleRetry = async (id) => {
    setRetrying(id);
    try {
      await api.retryDlq(id);
      onRefresh();
    } catch (err) {
      alert(err.message);
    } finally {
      setRetrying(null);
    }
  };

  if (!entries.length) {
    return <p className="text-slate-400 text-sm">Dead letter queue is empty.</p>;
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="text-left text-slate-400 border-b border-slate-700">
            <th className="py-2 pr-3">Job ID</th>
            <th className="py-2 pr-3">Error</th>
            <th className="py-2 pr-3">AI Summary</th>
            <th className="py-2 pr-3">Failed At</th>
            <th className="py-2">Action</th>
          </tr>
        </thead>
        <tbody>
          {entries.map((entry) => (
            <tr key={entry.id} className="border-b border-slate-800 align-top">
              <td className="py-2 pr-3">{entry.job?.id ?? '—'}</td>
              <td className="py-2 pr-3 max-w-xs text-red-300">{entry.errorMessage}</td>
              <td className="py-2 pr-3 max-w-md text-slate-300">{entry.aiSummary || 'Generating summary…'}</td>
              <td className="py-2 pr-3">{entry.failedAt?.replace('T', ' ').slice(0, 19)}</td>
              <td className="py-2">
                <button
                  disabled={retrying === entry.id || !entry.job}
                  onClick={() => handleRetry(entry.id)}
                  className="text-indigo-400 hover:underline text-xs disabled:opacity-40"
                >
                  {retrying === entry.id ? 'Retrying…' : 'Retry'}
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
