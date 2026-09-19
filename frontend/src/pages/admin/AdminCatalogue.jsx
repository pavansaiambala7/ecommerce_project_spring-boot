import { useCallback, useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Checkbox from '@mui/material/Checkbox';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import Divider from '@mui/material/Divider';
import FormControlLabel from '@mui/material/FormControlLabel';
import LinearProgress from '@mui/material/LinearProgress';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import UploadFileIcon from '@mui/icons-material/UploadFile';
import { api } from '../../api/client';

const number = (n) => new Intl.NumberFormat('en-IN').format(n ?? 0);

/** Job state drives the chip colour, so a stalled run is visible at a glance. */
const STATE_COLOUR = { RUNNING: 'info', DONE: 'success', FAILED: 'error', STOPPED: 'warning', IDLE: 'default' };

function Stat({ label, value }) {
  return (
    <Card>
      <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
        <Typography variant="caption" color="text.secondary" sx={{ textTransform: 'uppercase' }}>
          {label}
        </Typography>
        <Typography variant="h3" sx={{ mt: 0.5 }}>
          {value}
        </Typography>
      </CardContent>
    </Card>
  );
}

const STATS_GRID = {
  display: 'grid',
  gridTemplateColumns: { xs: 'repeat(2, 1fr)', md: 'repeat(4, 1fr)', lg: 'repeat(5, 1fr)' },
  gap: 1.5,
  mt: 2,
};

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
      setJob(
        method === 'start'
          ? await api.post('/api/admin/catalogue/embeddings')
          : await api.del('/api/admin/catalogue/embeddings'),
      );
    } catch (err) {
      setError(err.message);
    }
  }

  // The job reports what it has done and what is left, which together give the
  // only honest total: nothing else knows how many rows need a vector.
  const done = job ? job.embedded : 0;
  const outstanding = job && job.remaining >= 0 ? job.remaining : 0;
  const percent = done + outstanding > 0 ? (done * 100) / (done + outstanding) : 0;

  return (
    <>
      <Typography variant="h1" sx={{ mb: 2 }}>
        Catalogue import
      </Typography>

      <Paper sx={{ p: { xs: 2, sm: 3 }, mb: 3 }}>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Upload the CSV from <code>tools/generate_catalogue.py</code> (plain or <code>.gz</code>). Products are
          matched on <code>external_id</code>, so uploading the same file again updates rather than duplicates.
        </Typography>

        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}

        <Box component="form" onSubmit={upload}>
          <Button component="label" variant="outlined" startIcon={<UploadFileIcon />}>
            {file ? file.name : 'Choose CSV file'}
            <input
              type="file"
              hidden
              accept=".csv,.gz,text/csv,application/gzip"
              onChange={(event) => setFile(event.target.files[0])}
            />
          </Button>
          {file && (
            <Typography variant="caption" color="text.secondary" sx={{ ml: 1.5 }}>
              {(file.size / (1024 * 1024)).toFixed(1)} MB
            </Typography>
          )}

          <FormControlLabel
            // `display: block` here would drop the label below the box; the
            // label has to stay on the control's own line.
            sx={{ display: 'flex', width: 'fit-content', mt: 1.5 }}
            control={
              <Checkbox
                size="small"
                checked={embedAfter}
                onChange={(event) => setEmbedAfter(event.target.checked)}
              />
            }
            label={<Typography variant="body2">Start generating search embeddings when the import finishes</Typography>}
          />

          <Button
            type="submit"
            variant="contained"
            disabled={!file || uploading}
            startIcon={uploading ? <CircularProgress size={16} color="inherit" /> : null}
            sx={{ mt: 1 }}
          >
            {uploading ? 'Importing… this can take a minute' : 'Import'}
          </Button>
        </Box>

        {result && (
          <Box sx={STATS_GRID}>
            <Stat label="Rows read" value={number(result.rowsRead)} />
            <Stat label="New" value={number(result.inserted)} />
            <Stat label="Updated" value={number(result.updated)} />
            <Stat label="Unknown department" value={number(result.skippedUnknownDepartment)} />
            <Stat label="Invalid rows" value={number(result.skippedInvalid)} />
          </Box>
        )}
      </Paper>

      <Paper sx={{ p: { xs: 2, sm: 3 } }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <Typography variant="h2">Search embeddings</Typography>
          {job && <Chip label={job.state} size="small" color={STATE_COLOUR[job.state] ?? 'default'} />}
        </Box>

        {job && (
          <>
            {job.state === 'RUNNING' && (
              <LinearProgress
                variant={job.remaining >= 0 ? 'determinate' : 'indeterminate'}
                value={percent}
                sx={{ mt: 2, height: 8, borderRadius: 4 }}
              />
            )}

            <Box sx={STATS_GRID}>
              <Stat label="Embedded this run" value={number(job.embedded)} />
              <Stat label="Still missing" value={job.remaining < 0 ? '?' : number(job.remaining)} />
              {job.skipped > 0 && <Stat label="Skipped" value={number(job.skipped)} />}
            </Box>

            {job.backoffSeconds > 0 && (
              <Alert severity="info" sx={{ mt: 2 }}>
                Gemini quota reached — waiting {job.backoffSeconds}s before retrying.
              </Alert>
            )}
            {job.lastError && job.state !== 'RUNNING' && (
              <Alert severity="error" sx={{ mt: 2 }}>
                {job.lastError}
              </Alert>
            )}

            <Divider sx={{ my: 2 }} />

            <Box sx={{ display: 'flex', gap: 1.5 }}>
              <Button
                variant="contained"
                disabled={job.state === 'RUNNING' || job.remaining === 0}
                onClick={() => control('start')}
              >
                {job.state === 'STOPPED' || job.state === 'FAILED' ? 'Resume' : 'Start'}
              </Button>
              <Button variant="outlined" disabled={job.state !== 'RUNNING'} onClick={() => control('stop')}>
                Stop
              </Button>
            </Box>
          </>
        )}
      </Paper>
    </>
  );
}
