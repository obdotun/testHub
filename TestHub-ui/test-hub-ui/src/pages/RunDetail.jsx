import React, { useEffect, useState, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import Box from '@mui/material/Box';
import Grid from '@mui/material/Grid';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import Tabs from '@mui/material/Tabs';
import Tab from '@mui/material/Tab';
import Stack from '@mui/material/Stack';
import Alert from '@mui/material/Alert';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import LinearProgress from '@mui/material/LinearProgress';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import { runApi, reportUrl } from '../api/client';
import { useWebSocket } from '../hooks/useWebSocket';
import StatusChip from '../components/StatusChip';
import Terminal from '../components/Terminal';

function fmt(s) {
  if (!s) return '—';
  return s < 60 ? `${s}s` : `${Math.floor(s / 60)}m ${s % 60}s`;
}

function fmtDate(dt) {
  if (!dt) return '—';
  return new Date(dt).toLocaleString('fr-FR', {
    day: '2-digit', month: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  });
}

export default function RunDetail() {
  const { id }   = useParams();
  const navigate = useNavigate();

  const [run,        setRun]        = useState(null);
  const [logs,       setLogs]       = useState([]);
  const [logsLoaded, setLogsLoaded] = useState(false);
  const [tab,        setTab]        = useState(0);

  const isActive = run?.status === 'RUNNING' || run?.status === 'PENDING';

  const refresh = useCallback(() => {
    runApi.getById(id).then(setRun);
  }, [id]);

  useEffect(() => {
    refresh();
    fetch(`/api/runs/${id}/logs`)
      .then(r => r.json())
      .then(data => { if (Array.isArray(data)) setLogs(data); setLogsLoaded(true); })
      .catch(() => setLogsLoaded(true));
  }, [id, refresh]);

  useEffect(() => {
    if (!isActive) return;
    const t = setInterval(refresh, 3000);
    return () => clearInterval(t);
  }, [isActive, refresh]);

  useWebSocket(
    isActive ? `/topic/runs/${id}/logs` : null,
    useCallback(msg => {
      setLogs(prev => {
        const isDuplicate = prev.some(
          l => l.text === msg.text && l.timestamp === msg.timestamp && l.level === msg.level
        );
        return isDuplicate ? prev : [...prev, msg];
      });
      if (msg.text?.includes('Fin exécution')) setTimeout(refresh, 800);
    }, [refresh])
  );

  if (!run) return (
    <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%' }}>
      <CircularProgress />
    </Box>
  );

  const total    = (run.passed ?? 0) + (run.failed ?? 0) + (run.skipped ?? 0);
  const passRate = total > 0 ? Math.round((run.passed / total) * 100) : 0;

  return (
    <Box sx={{ p: 3, overflow: 'auto', flex: 1 }}>
      <Box sx={{ mb: 3 }}>
        <Button size="small" startIcon={<ArrowBackIcon />}
          onClick={() => navigate(`/projects/${run.projectId}`)} sx={{ mb: 1 }}>
          {run.projectName}
        </Button>
        <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between' }}>
          <Box>
            <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 0.5 }}>
              <Typography variant="h5">{run.label}</Typography>
              <StatusChip status={run.status} />
              {run.executedWithPabot && <Chip label="pabot" size="small" color="primary" variant="outlined" />}
            </Stack>
            <Typography variant="caption" color="text.disabled" sx={{ fontFamily: 'monospace' }}>
              {run.target}
            </Typography>
          </Box>
          {run.reportPath && (
            <Button variant="outlined" size="small" startIcon={<OpenInNewIcon />}
              href={reportUrl.report(id)} target="_blank" rel="noreferrer">
              Ouvrir rapport
            </Button>
          )}
        </Box>
      </Box>

      {isActive && <LinearProgress color="warning" sx={{ mb: 2, borderRadius: 1 }} />}

      <Grid container spacing={2} sx={{ mb: 3 }}>
        {[
          { label: 'Passés',  value: run.passed  ?? '—', color: 'success.main'   },
          { label: 'Échoués', value: run.failed  ?? '—', color: 'error.main'     },
          { label: 'Ignorés', value: run.skipped ?? '—', color: 'text.secondary' },
          { label: 'Durée',   value: fmt(run.durationSeconds), color: 'text.primary' },
          { label: 'Démarré', value: fmtDate(run.startedAt),  color: 'text.secondary', sm: true },
          { label: 'Terminé', value: fmtDate(run.finishedAt), color: 'text.secondary', sm: true },
        ].map(s => (
          <Grid item xs={6} sm={4} md={2} key={s.label}>
            <Paper sx={{ p: 2 }}>
              <Typography variant="caption" color="text.disabled"
                sx={{ textTransform: 'uppercase', letterSpacing: '0.08em', fontWeight: 600 }}>
                {s.label}
              </Typography>
              <Typography variant={s.sm ? 'body2' : 'h5'} sx={{
                color: s.color, fontFamily: '"JetBrains Mono", monospace',
                mt: 0.5, fontSize: s.sm ? 12 : undefined,
              }}>
                {s.value}
              </Typography>
            </Paper>
          </Grid>
        ))}
      </Grid>

      {total > 0 && (
        <Paper sx={{ p: 2, mb: 3 }}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
            <Typography variant="caption" color="text.disabled"
              sx={{ textTransform: 'uppercase', letterSpacing: '0.08em', fontWeight: 600 }}>
              Taux de succès
            </Typography>
            <Typography variant="caption" fontWeight={700} sx={{
              color: passRate === 100 ? 'success.main' : passRate > 50 ? 'warning.main' : 'error.main',
            }}>
              {passRate}%
            </Typography>
          </Box>
          <LinearProgress variant="determinate" value={passRate}
            color={passRate === 100 ? 'success' : passRate > 50 ? 'warning' : 'error'}
            sx={{ height: 8, borderRadius: 4 }} />
        </Paper>
      )}

      {run.errorMessage && (
        <Alert severity="error" sx={{ mb: 2, fontFamily: 'monospace', fontSize: 12 }}>
          {run.errorMessage}
        </Alert>
      )}

      <Paper sx={{ mb: 2 }}>
        <Tabs value={tab} onChange={(_, v) => setTab(v)}
          sx={{ borderBottom: '1px solid', borderColor: 'divider', px: 2 }}>
          <Tab label={`📟 Logs (${logs.length})`} />
          <Tab label="📊 report.html" disabled={!run.reportPath} />
          <Tab label="📋 log.html"    disabled={!run.reportPath} />
        </Tabs>
      </Paper>

      {tab === 0 && (
        <Box>
          {!logsLoaded && (
            <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1.5 }}>
              <CircularProgress size={14} />
              <Typography variant="caption" color="text.secondary">Chargement des logs…</Typography>
            </Stack>
          )}
          {isActive && logsLoaded && (
            <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1.5 }}>
              <CircularProgress size={14} color="warning" />
              <Typography variant="caption" color="warning.main">Exécution en cours…</Typography>
            </Stack>
          )}
          {logsLoaded && logs.length === 0 && !isActive && (
            <Alert severity="info" sx={{ mb: 2 }}>Aucun log disponible pour ce run.</Alert>
          )}
          <Terminal logs={logs} height={560} title={`run #${id} — ${run.label}`} />
        </Box>
      )}

      {tab === 1 && (
        <Paper sx={{ overflow: 'hidden', height: 600 }}>
          <iframe src={reportUrl.report(id)} style={{ width: '100%', height: '100%', border: 'none' }} title="report.html" />
        </Paper>
      )}

      {tab === 2 && (
        <Paper sx={{ overflow: 'hidden', height: 600 }}>
          <iframe src={reportUrl.log(id)} style={{ width: '100%', height: '100%', border: 'none' }} title="log.html" />
        </Paper>
      )}
    </Box>
  );
}