import { useState } from 'react';
import { api, setToken } from '../api';

export default function LoginPage({ onLogin }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [isRegister, setIsRegister] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      if (isRegister) {
        await api.register(username, password);
      }
      const res = await api.login(username, password);
      setToken(res.token);
      onLogin();
    } catch (err) {
      // If the user tried to register but the username already exists,
      // attempt to log them in automatically to reduce friction.
      if (isRegister && err.message && err.message.toLowerCase().includes('username already exists')) {
        try {
          const res = await api.login(username, password);
          setToken(res.token);
          onLogin();
          return;
        } catch (loginErr) {
          setError(loginErr.message || 'Login failed after registration conflict');
          return;
        }
      }

      setError(err.message || 'Authentication error');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-4">
      <form onSubmit={handleSubmit} className="w-full max-w-md bg-slate-900 border border-slate-700 rounded-xl p-8 space-y-4">
        <h1 className="text-2xl font-semibold">Distributed Job Scheduler</h1>
        <p className="text-slate-400 text-sm">Sign in to manage queues, jobs, and workers.</p>

        {error && <div className="text-red-400 text-sm bg-red-950/40 border border-red-900 rounded p-2">{error}</div>}

        <label className="block text-sm">
          Username
          <input
            className="mt-1 w-full rounded bg-slate-800 border border-slate-600 px-3 py-2"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            required
          />
        </label>

        <label className="block text-sm">
          Password
          <input
            type="password"
            className="mt-1 w-full rounded bg-slate-800 border border-slate-600 px-3 py-2"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </label>

        <button
          type="submit"
          disabled={loading}
          className="w-full bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 rounded py-2 font-medium"
        >
          {loading ? 'Please wait…' : isRegister ? 'Register' : 'Login'}
        </button>

        <button
          type="button"
          className="text-sm text-slate-400 hover:text-slate-200"
          onClick={() => setIsRegister(!isRegister)}
        >
          {isRegister ? 'Already have an account? Login' : 'Need an account? Register'}
        </button>
      </form>
    </div>
  );
}
