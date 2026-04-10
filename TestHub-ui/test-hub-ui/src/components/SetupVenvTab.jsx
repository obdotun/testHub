import React, { useEffect, useState, useCallback } from 'react';
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';
import CircularProgress from '@mui/material/CircularProgress';
import RefreshIcon from '@mui/icons-material/Refresh';
import { useWebSocket } from '../hooks/useWebSocket';
import StatusChip from './StatusChip';
import Terminal from './Terminal';

export default function SetupVenvTab({ project, projectId, onReinstall }) {
  const [logs,       setLogs]       = useState([]);
  const [logsLoaded, setLogsLoaded] = useState(false);
  const [loading,    setLoading]    = useState(false);

  const isInstalling = project?.venvStatus === 'INSTALLING';

  // ── Charger les logs persistés au montage ────────────────────────────────
  useEffect(() => {
    fetch(`/api/projects/${projectId}/setup-logs`)
      .then(r => r.json())
      .then(data => {
        if (Array.isArray(data)) setLogs(data);
        setLogsLoaded(true);
      })
      .catch(() => setLogsLoaded(true));
  }, [projectId]);

  // ── WebSocket — logs temps réel pendant l'installation ───────────────────
  useWebSocket(
    isInstalling ? `/topic/projects/${projectId}/setup` : null,
    useCallback(msg => {
      setLogs(prev => {
        const isDuplicate = prev.some(
          l => l.text === msg.text && l.timestamp === msg.timestamp
        );
        return isDuplicate ? prev : [...prev, msg];
      });
    }, [])
  );

  // ── Réinstallation avec protection double clic ───────────────────────────
  const handleReinstall = async () => {
    setLoading(true);
    setLogs([]);
    setLogsLoaded(false);
    try {
      await onReinstall();
    } catch (e) {
      console.error('Erreur réinstallation:', e);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box>
      {/* ── Header ──────────────────────────────────────────────────────── */}
      <Stack direction="row" spacing={2} alignItems="center" sx={{ mb: 2 }}>
        <Typography variant="subtitle2">Installation du venv</Typography>
        <StatusChip status={project?.venvStatus} />

        {/* Bouton Relancer si ERROR ou NONE */}
        {(project?.venvStatus === 'ERROR' || project?.venvStatus === 'NONE') && (
          <Button
            size="small"
            variant="outlined"
            onClick={handleReinstall}
            disabled={loading}
            startIcon={loading ? <CircularProgress size={14} /> : <RefreshIcon />}
          >
            {loading ? 'Lancement…' : "Relancer l'installation"}
          </Button>
        )}

        {/* Bouton Réinstaller si READY */}
        {project?.venvStatus === 'READY' && (
          <Button
            size="small"
            variant="outlined"
            color="warning"
            onClick={handleReinstall}
            disabled={loading}
            startIcon={loading ? <CircularProgress size={14} /> : <RefreshIcon />}
          >
            {loading ? 'Lancement…' : 'Réinstaller'}
          </Button>
        )}
      </Stack>

      {/* ── Message d'erreur si venvStatus = ERROR ─────────────────────── */}
      {project?.venvStatus === 'ERROR' && project?.venvError && (
        <Alert severity="error" sx={{ mb: 2, fontFamily: 'monospace', fontSize: 12 }}>
          <strong>Cause de l'échec :</strong><br />
          {project.venvError}
        </Alert>
      )}

      {/* ── Message si venv prêt ─────────────────────────────────────────── */}
      {project?.venvStatus === 'READY' && logs.length > 0 && (
        <Alert severity="success" sx={{ mb: 2, fontSize: 12 }}>
          Venv installé avec succès — logs de la dernière installation ci-dessous.
        </Alert>
      )}

      {/* ── Chargement logs ─────────────────────────────────────────────── */}
      {!logsLoaded && (
        <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1.5 }}>
          <CircularProgress size={14} />
          <Typography variant="caption" color="text.secondary">
            Chargement des logs…
          </Typography>
        </Stack>
      )}

      {/* ── Installation en cours ────────────────────────────────────────── */}
      {isInstalling && (
        <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1.5 }}>
          <CircularProgress size={14} color="warning" />
          <Typography variant="caption" color="warning.main">
            Installation en cours…
          </Typography>
        </Stack>
      )}

      {/* ── Aucun log disponible ─────────────────────────────────────────── */}
      {logsLoaded && logs.length === 0 && !isInstalling && (
        <Alert severity="info" sx={{ mb: 2 }}>
          {project?.venvStatus === 'NONE'
            ? "Le venv n'a pas encore été installé. Cliquez sur \"Relancer l'installation\"."
            : 'Aucun log disponible pour ce projet.'}
        </Alert>
      )}

      {/* ── Terminal ─────────────────────────────────────────────────────── */}
      {logs.length > 0 && (
        <Terminal
          logs={logs}
          height={520}
          title={`venv — ${project?.name}`}
        />
      )}
    </Box>
  );
}