import { useEffect, useRef, useState } from 'react';

export function usePolling(fetchFn, intervalMs = 4000, enabled = true) {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const fetchRef = useRef(fetchFn);

  useEffect(() => {
    fetchRef.current = fetchFn;
  }, [fetchFn]);

  useEffect(() => {
    if (!enabled) return undefined;

    let active = true;

    const run = async () => {
      try {
        const result = await fetchRef.current();
        if (active) {
          setData(result);
          setError(null);
        }
      } catch (err) {
        if (active) setError(err.message);
      } finally {
        if (active) setLoading(false);
      }
    };

    run();
    const id = setInterval(run, intervalMs);
    return () => {
      active = false;
      clearInterval(id);
    };
  }, [intervalMs, enabled]);

  return { data, error, loading, refresh: () => fetchRef.current().then(setData) };
}
