import { useCallback, useEffect, useState } from 'react';
import { api } from '../../api/client';

const number = (n) => new Intl.NumberFormat('en-IN').format(n ?? 0);

/**
 * Bulk catalogue import and the embedding job that follows it.
 *
 * <p>The file goes up as the raw request body, so a 12 MB CSV streams into the
 * database without the browser or the server holding it in memory. A .gz file
 * is detected by the server from its contents.
 */
export default function AdminCatalogue() {
  const [file, setFile] = useState(null);
  const [embedAfter, setEmbedAfter] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const [job, setJob] = useState(null);

  const loadStatus = useCallback(() => {
    api.get('/api/admin/catalogue/embeddings').then(setJob).catch(() => {});
  }, []);

  useEffect(loadStatus, [loadStatus]);

  // Poll only while the job is running; an idle page makes no requests.
  useEffect(() => {
    if (job?.state !== 'RUNNING') return undefined;
    const timer = setInterval(loadStatus, 3000);
    return () => clearInterval(timer);
  }, [job?.state, loadStatus]);

  async function upload(event) {
    event.preventDefault();
    if (!file) return;
    setUploading(true);
    setError(null);
    setResult(null);
    try {
      const imported = await api.post(`/api/admin/catalogue/import?embed=${embedAfter}`, undefined, {
        rawBody: file,
        headers: { 'Content-Type': file.name.endsWith('.gz') ? 'application/gzip' : 'text/csv' },
      });
      setResult(imported);
      loadStatus();
    } catch (err) {
      setError(err.message);
    } finally {
      setUploading(false);
    }
  }

  async function control(method) {
    setError(null);
    try {
      setJob(method === 'start'
        ? await api.post('/api/admin/catalogue/embeddings')
        : await api.del('/api/admin/catalogue/embeddings'));
    } catch (err) {
      setError(err.message);
    }
  }

  return (
    <div className="panel">
      <h1 className="section-title">Catalogue import</h1>
      <p className="meta-row">
        Upload the CSV from <code>tools/generate_catalogue.py</code> (plain or <code>.gz</code>). Products are matched
        on <code>external_id</code>, so uploading the same file again updates rather than duplicates.
      </p>

      {error && <div className="error">{error}</div>}

      <form className="admin-form" onSubmit={upload}>
        <label>
          CSV file
          <input type="file" accept=".csv,.gz,text/csv,application/gzip" onChange={(e) => setFile(e.target.files[0])} />
        </label>
        <label className="checkbox-row">
          <input type="checkbox" checked={embedAfter} onChange={(e) => setEmbedAfter(e.target.checked)} />
          Start generating search embeddings when the import finishes
        </label>
        <div className="admin-actions">
          <button type="submit" className="btn" disabled={!file || uploading}>
            {uploading ? 'Importing… this can take a minute' : 'Import'}
          </button>
          {file && <span className="meta-row">{(file.size / (1024 * 1024)).toFixed(1)} MB</span>}
        </div>
      </form>

      {result && (
        <div className="stat-row">
          <div className="stat"><span>Rows read</span><strong>{number(result.rowsRead)}</strong></div>
          <div className="stat"><span>New</span><strong>{number(result.inserted)}</strong></div>
          <div className="stat"><span>Updated</span><strong>{number(result.updated)}</strong></div>
          <div className="stat"><span>Unknown department</span><strong>{number(result.skippedUnknownDepartment)}</strong></div>
          <div className="stat"><span>Invalid rows</span><strong>{number(result.skippedInvalid)}</strong></div>
        </div>
      )}

      <h2 className="section-title" style={{ marginTop: 24 }}>Search embeddings</h2>
      {job && (
        <>
          <div className="stat-row">
            <div className="stat"><span>Status</span><strong>{job.state}</strong></div>
            <div className="stat"><span>Embedded this run</span><strong>{number(job.embedded)}</strong></div>
            <div className="stat"><span>Still missing</span><strong>{job.remaining < 0 ? '?' : number(job.remaining)}</strong></div>
            {job.skipped > 0 && <div className="stat"><span>Skipped</span><strong>{number(job.skipped)}</strong></div>}
          </div>
          {job.backoffSeconds > 0 && (
            <p className="meta-row">Gemini quota reached - waiting {job.backoffSeconds}s before retrying.</p>
          )}
          {job.lastError && job.state !== 'RUNNING' && <div className="error">{job.lastError}</div>}
          <div className="admin-actions">
            <button type="button" className="btn" disabled={job.state === 'RUNNING' || job.remaining === 0}
              onClick={() => control('start')}>
              {job.state === 'STOPPED' || job.state === 'FAILED' ? 'Resume' : 'Start'}
            </button>
            <button type="button" className="btn-plain" disabled={job.state !== 'RUNNING'}
              onClick={() => control('stop')}>
              Stop
            </button>
          </div>
        </>
      )}
    </div>
  );
}
