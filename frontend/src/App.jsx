import { useCallback, useEffect, useMemo, useState } from 'react';
import { api, clearToken, getToken } from './api';
import { usePolling } from './hooks/usePolling';
import LoginPage from './components/LoginPage';
import QueueList from './components/QueueList';
import JobExplorer from './components/JobExplorer';
import WorkerGrid from './components/WorkerGrid';
import ThroughputChart from './components/ThroughputChart';
import DlqView from './components/DlqView';

function Panel({ title, children, action }) {
  return (
    <section className="bg-slate-900 border border-slate-800 rounded-xl p-5 space-y-4">
      <div className="flex items-center justify-between gap-3">
        <h2 className="text-lg font-semibold">{title}</h2>
        {action}
      </div>
      {children}
    </section>
  );
}

function StatCard({ label, value }) {
  return (
    <div className="bg-slate-900 border border-slate-800 rounded-lg p-4">
      <p className="text-xs uppercase tracking-wide text-slate-400">{label}</p>
      <p className="text-2xl font-semibold mt-1">{value ?? '—'}</p>
    </div>
  );
}

export default function App() {
  const [authed, setAuthed] = useState(!!getToken());
  const [project, setProject] = useState(null);
  const [queues, setQueues] = useState([]);
  const [jobFilters, setJobFilters] = useState({ queueId: '', status: '', start: '', end: '', page: 0, size: 10 });
  const [jobsPage, setJobsPage] = useState(null);
  const [newQueueName, setNewQueueName] = useState('');
  const [newJobPayload, setNewJobPayload] = useState('sleep:500');
  const [selectedQueueId, setSelectedQueueId] = useState('');
  const [submitMsg, setSubmitMsg] = useState('');

  const loadJobs = useCallback(async () => {
    const params = {
      page: jobFilters.page,
      size: jobFilters.size,
    };
    if (jobFilters.queueId) params.queueId = jobFilters.queueId;
    if (jobFilters.status) params.status = jobFilters.status;
    if (jobFilters.start) params.start = new Date(jobFilters.start).toISOString();
    if (jobFilters.end) params.end = new Date(jobFilters.end).toISOString();
    const data = await api.getJobs(params);
    setJobsPage(data);
    return data;
  }, [jobFilters]);

  useEffect(() => {
    if (!authed) return;
    loadJobs().catch(() => {});
  }, [authed, loadJobs]);

  const { data: queueHealth } = usePolling(() => api.getQueueHealth(), 4000, authed);
  const { data: metrics } = usePolling(() => api.getMetrics(), 4000, authed);
  const { data: throughput } = usePolling(() => api.getThroughput(24), 5000, authed);
  const { data: workers } = usePolling(() => api.getWorkerStatuses(), 4000, authed);
  const { data: dlqPage, refresh: refreshDlq } = usePolling(() => api.getDlq(0, 20), 4000, authed);

  useEffect(() => {
    if (!authed) return;
    (async () => {
      try {
        const p = await api.getDefaultProject();
        setProject(p);
        const q = await api.getQueues();
        setQueues(q);
        if (q.length && !selectedQueueId) setSelectedQueueId(String(q[0].id));
      } catch {
        const p = await api.createProject('Default Project');
        setProject(p);
      }
    })();
  }, [authed, selectedQueueId]);

  const handleLogout = () => {
    clearToken();
    setAuthed(false);
  };

  const handleCreateQueue = async () => {
    if (!newQueueName.trim() || !project) return;
    await api.createQueue({
      name: newQueueName.trim(),
      projectId: project.id,
      priority: 1,
      concurrencyLimit: 5,
      paused: false,
    });
    setNewQueueName('');
    const q = await api.getQueues();
    setQueues(q);
  };

  const handleCreateJob = async () => {
    if (!selectedQueueId || !newJobPayload.trim()) return;
    await api.createJob(Number(selectedQueueId), { payload: newJobPayload.trim(), priority: 1, maxRetries: 3 });
    setSubmitMsg('Job submitted');
    setTimeout(() => setSubmitMsg(''), 2000);
    loadJobs();
  };

  const handlePause = async (id) => {
    await api.pauseQueue(id);
  };

  const handleResume = async (id) => {
    await api.resumeQueue(id);
  };

  const statusCounts = metrics?.statusCounts || {};

  const totalJobs = useMemo(
    () => Object.values(statusCounts).reduce((a, b) => a + b, 0),
    [statusCounts],
  );

  if (!authed) {
    return <LoginPage onLogin={() => setAuthed(true)} />;
  }

  return (
    <div className="min-h-screen">
      <header className="border-b border-slate-800 bg-slate-950/80 sticky top-0 z-10">
        <div className="max-w-7xl mx-auto px-4 py-4 flex items-center justify-between">
          <div>
            <h1 className="text-xl font-semibold">Job Scheduler Dashboard</h1>
            <p className="text-xs text-slate-400">Live updates every 4s · Project: {project?.name || '…'}</p>
          </div>
          <button onClick={handleLogout} className="text-sm text-slate-400 hover:text-white">Logout</button>
        </div>
      </header>

      <main className="max-w-7xl mx-auto px-4 py-6 space-y-6">
        <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
          <StatCard label="Total Jobs" value={totalJobs} />
          <StatCard label="Running" value={statusCounts.RUNNING || 0} />
          <StatCard label="Queued" value={(statusCounts.QUEUED || 0) + (statusCounts.SCHEDULED || 0)} />
          <StatCard label="Active Workers" value={metrics?.activeWorkers ?? 0} />
        </div>

        <Panel
          title="Queues"
          action={
            <div className="flex gap-2">
              <input
                className="bg-slate-800 border border-slate-600 rounded px-2 py-1 text-sm"
                placeholder="New queue name"
                value={newQueueName}
                onChange={(e) => setNewQueueName(e.target.value)}
              />
              <button onClick={handleCreateQueue} className="text-sm bg-indigo-600 hover:bg-indigo-500 rounded px-3 py-1">
                Add
              </button>
            </div>
          }
        >
          <QueueList queues={queueHealth || queues} onPause={handlePause} onResume={handleResume} />
        </Panel>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <Panel title="Submit Job">
            <div className="space-y-3">
              <select
                className="w-full bg-slate-800 border border-slate-600 rounded px-2 py-2 text-sm"
                value={selectedQueueId}
                onChange={(e) => setSelectedQueueId(e.target.value)}
              >
                <option value="">Select queue</option>
                {queues.map((q) => (
                  <option key={q.id} value={q.id}>{q.name}</option>
                ))}
              </select>
              <input
                className="w-full bg-slate-800 border border-slate-600 rounded px-2 py-2 text-sm"
                value={newJobPayload}
                onChange={(e) => setNewJobPayload(e.target.value)}
                placeholder="Payload (e.g. sleep:1000, fail-simulation, https://...)"
              />
              <button onClick={handleCreateJob} className="bg-indigo-600 hover:bg-indigo-500 rounded px-4 py-2 text-sm">
                Submit Job
              </button>
              {submitMsg && <p className="text-emerald-400 text-sm">{submitMsg}</p>}
            </div>
          </Panel>

          <Panel title="Throughput (completed / hour)">
            <ThroughputChart data={throughput} />
          </Panel>
        </div>

        <Panel title="Job Explorer">
          <JobExplorer
            jobsPage={jobsPage}
            filters={jobFilters}
            queues={queues}
            onFilterChange={setJobFilters}
            onPageChange={(page) => setJobFilters((f) => ({ ...f, page }))}
          />
        </Panel>

        <Panel title="Workers">
          <WorkerGrid workers={workers} />
        </Panel>

        <Panel title="Dead Letter Queue">
          <DlqView dlqPage={dlqPage} onRefresh={refreshDlq} />
        </Panel>
      </main>
    </div>
  );
}
