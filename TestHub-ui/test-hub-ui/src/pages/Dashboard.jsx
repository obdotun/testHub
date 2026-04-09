import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Box from '@mui/material/Box';
import Grid from '@mui/material/Grid';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import Divider from '@mui/material/Divider';
import CircularProgress from '@mui/material/CircularProgress';
import Stack from '@mui/material/Stack';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import { projectApi, runApi } from '../api/client';
import StatusChip from '../components/StatusChip';

function StatCard({ label, value, color = 'text.primary' }) {
  return (
    <Paper sx={{ p: 2.5 }}>
      <Typography variant="caption" color="text.disabled"
        sx={{ textTransform: 'uppercase', letterSpacing: '0.08em', fontWeight: 600 }}>
        {label}
      </Typography>
      <Typography variant="h4" sx={{ mt: 0.5, color, fontFamily: '"JetBrains Mono", monospace' }}>
        {value}
      </Typography>
    </Paper>
  );
}

function fmt(dt) {
  if (!dt) return '—';
  return new Date(dt).toLocaleString('fr-FR', {
    day: '2-digit', month: '2-digit',
    hour: '2-digit', minute: '2-digit',
  });
}

function fmtDur(s) {
  if (!s) return '—';
  return s < 60 ? `${s}s` : `${Math.floor(s / 60)}m ${s % 60}s`;
}

export default function Dashboard() {
  const [projects, setProjects] = useState([]);
  const [runs,     setRuns]     = useState([]);
  const [loading,  setLoading]  = useState(true);
  const navigate = useNavigate();

  useEffect(() => {
    Promise.all([projectApi.getAll(), runApi.getAll()])
      .then(([p, r]) => { setProjects(p); setRuns(r.slice(0, 20)); })
      .finally(() => setLoading(false));
  }, []);

  if (loading) return (
    <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%' }}>
      <CircularProgress />
    </Box>
  );

  const passed  = runs.filter(r => r.status === 'PASSED').length;
  const failed  = runs.filter(r => r.status === 'FAILED').length;
  const running = runs.filter(r => r.status === 'RUNNING').length;

  return (
    <Box sx={{ p: 3, overflow: 'auto', flex: 1 }}>
      <Typography variant="h5" sx={{ mb: 3 }}>Dashboard</Typography>

      {/* Stats */}
      <Grid container spacing={2} sx={{ mb: 3 }}>
        {[
          { label: 'Projets',    value: projects.length, color: 'primary.main' },
          { label: 'Runs total', value: runs.length,     color: 'text.primary' },
          { label: 'Passed',     value: passed,          color: 'success.main' },
          { label: 'Failed',     value: failed,          color: 'error.main'   },
          ...(running > 0 ? [{ label: 'En cours', value: running, color: 'warning.main' }] : []),
        ].map(s => (
          <Grid item xs={6} sm={4} md={2.4} key={s.label}>
            <StatCard {...s} />
          </Grid>
        ))}
      </Grid>

      <Grid container spacing={2}>
        {/* Projets */}
        <Grid item xs={12} md={5}>
          <Paper sx={{ p: 0, overflow: 'hidden' }}>
            <Box sx={{ display: 'flex', alignItems: 'center',
              justifyContent: 'space-between', px: 2.5, py: 2 }}>
              <Typography variant="subtitle1">Projets</Typography>
              <Button size="small" endIcon={<ArrowForwardIcon />}
                onClick={() => navigate('/projects')}>
                Voir tout
              </Button>
            </Box>
            <Divider />
            {projects.length === 0 ? (
              <Box sx={{ p: 4, textAlign: 'center' }}>
                <Typography color="text.disabled">Aucun projet</Typography>
                <Button variant="contained" size="small" sx={{ mt: 2 }}
                  onClick={() => navigate('/projects')}>
                  Créer un projet
                </Button>
              </Box>
            ) : (
              projects.map((p, i) => (
                <Box key={p.id}>
                  <Box
                    onClick={() => navigate(`/projects/${p.id}`)}
                    sx={{
                      px: 2.5, py: 1.5, cursor: 'pointer',
                      display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                      '&:hover': { bgcolor: '#1c2030' }, transition: 'background 0.15s',
                    }}
                  >
                    <Box>
                      <Typography variant="body2" fontWeight={600}>{p.name}</Typography>
                      <Typography variant="caption" color="text.disabled">
                        {p.totalTestCases} tests · {p.totalFiles} fichiers
                      </Typography>
                    </Box>
                    <StatusChip status={p.venvStatus} />
                  </Box>
                  {i < projects.length - 1 && <Divider />}
                </Box>
              ))
            )}
          </Paper>
        </Grid>

        {/* Derniers runs */}
        <Grid item xs={12} md={7}>
          <Paper sx={{ p: 0, overflow: 'hidden' }}>
            <Box sx={{ display: 'flex', alignItems: 'center',
              justifyContent: 'space-between', px: 2.5, py: 2 }}>
              <Typography variant="subtitle1">Derniers runs</Typography>
              <Button size="small" endIcon={<ArrowForwardIcon />}
                onClick={() => navigate('/runs')}>
                Voir tout
              </Button>
            </Box>
            <Divider />
            {runs.length === 0 ? (
              <Box sx={{ p: 4, textAlign: 'center' }}>
                <Typography color="text.disabled">Aucun run pour l'instant</Typography>
              </Box>
            ) : (
              runs.slice(0, 8).map((r, i) => (
                <Box key={r.id}>
                  <Box
                    onClick={() => navigate(`/runs/${r.id}`)}
                    sx={{
                      px: 2.5, py: 1.5, cursor: 'pointer',
                      display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                      '&:hover': { bgcolor: '#1c2030' }, transition: 'background 0.15s',
                    }}
                  >
                    <Box sx={{ flex: 1, minWidth: 0, mr: 2 }}>
                      <Typography variant="body2" fontWeight={600} noWrap>{r.label}</Typography>
                      <Typography variant="caption" color="text.disabled">
                        {r.projectName} · {fmt(r.startedAt)}
                      </Typography>
                    </Box>
                    <Stack direction="row" spacing={1} alignItems="center">
                      <Typography variant="caption" color="text.disabled"
                        sx={{ fontFamily: '"JetBrains Mono", monospace' }}>
                        {fmtDur(r.durationSeconds)}
                      </Typography>
                      <StatusChip status={r.status} />
                    </Stack>
                  </Box>
                  {i < Math.min(runs.length, 8) - 1 && <Divider />}
                </Box>
              ))
            )}
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
}