import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import Stack from '@mui/material/Stack';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import CircularProgress from '@mui/material/CircularProgress';
import Chip from '@mui/material/Chip';
import { DataGrid } from '@mui/x-data-grid';
import { runApi } from '../api/client';
import StatusChip from '../components/StatusChip';

function fmtDate(dt) {
  if (!dt) return '—';
  return new Date(dt).toLocaleString('fr-FR', {
    day: '2-digit', month: '2-digit',
    hour: '2-digit', minute: '2-digit',
  });
}

const COLUMNS = (navigate) => [
  {
    field: 'id', headerName: '#', width: 70,
    renderCell: p => (
      <Typography variant="caption" sx={{ fontFamily: 'monospace', color: 'text.disabled' }}>
        #{p.value}
      </Typography>
    ),
  },
  {
    field: 'label', headerName: 'Label', flex: 1,
    renderCell: p => (
      <Typography variant="body2" fontWeight={600}>{p.value}</Typography>
    ),
  },
  {
    field: 'projectName', headerName: 'Projet', width: 150,
    renderCell: p => (
      <Chip label={p.value} size="small" variant="outlined" sx={{ fontSize: 11 }} />
    ),
  },
  {
    field: 'mode', headerName: 'Mode', width: 120,
    renderCell: p => (
      <Chip label={p.value} size="small" sx={{ fontSize: 10 }} />
    ),
  },
  {
    field: 'status', headerName: 'Statut', width: 130,
    renderCell: p => <StatusChip status={p.value} />,
  },
  {
    field: 'passed', headerName: 'Passés', width: 80,
    renderCell: p => (
      <Typography sx={{ color: 'success.main', fontFamily: 'monospace', fontSize: 13 }}>
        {p.value ?? '—'}
      </Typography>
    ),
  },
  {
    field: 'failed', headerName: 'Échoués', width: 90,
    renderCell: p => (
      <Typography sx={{ color: 'error.main', fontFamily: 'monospace', fontSize: 13 }}>
        {p.value ?? '—'}
      </Typography>
    ),
  },
  {
    field: 'durationSeconds', headerName: 'Durée', width: 90,
    renderCell: p => (
      <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
        {p.value ? `${p.value}s` : '—'}
      </Typography>
    ),
  },
  {
    field: 'startedAt', headerName: 'Date', width: 140,
    renderCell: p => (
      <Typography variant="caption" color="text.secondary">{fmtDate(p.value)}</Typography>
    ),
  },
];

export default function Runs() {
  const [runs,    setRuns]    = useState([]);
  const [filter,  setFilter]  = useState('ALL');
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  useEffect(() => {
    runApi.getAll().then(setRuns).finally(() => setLoading(false));
  }, []);

  const filtered = filter === 'ALL' ? runs : runs.filter(r => r.status === filter);

  return (
    <Box sx={{ p: 3, overflow: 'auto', flex: 1 }}>
      <Box sx={{ display: 'flex', alignItems: 'center',
        justifyContent: 'space-between', mb: 3 }}>
        <Box>
          <Typography variant="h5">Exécutions</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
            Historique de tous les runs sur tous les projets
          </Typography>
        </Box>

        {/* Filtres */}
        <ToggleButtonGroup
          value={filter}
          exclusive
          onChange={(_, v) => v && setFilter(v)}
          size="small"
        >
          {['ALL', 'PASSED', 'FAILED', 'RUNNING', 'ERROR'].map(f => (
            <ToggleButton key={f} value={f} sx={{
              fontSize: 11, fontWeight: 600, px: 1.5,
              textTransform: 'none',
            }}>
              {f}
            </ToggleButton>
          ))}
        </ToggleButtonGroup>
      </Box>

      {loading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 8 }}>
          <CircularProgress />
        </Box>
      ) : (
        <Paper sx={{ height: 600 }}>
          <DataGrid
            rows={filtered}
            columns={COLUMNS(navigate)}
            pageSize={15}
            rowsPerPageOptions={[15, 30, 50]}
            disableSelectionOnClick
            onRowClick={p => navigate(`/runs/${p.id}`)}
            sx={{ border: 'none' }}
            localeText={{
              noRowsLabel: filter === 'ALL'
                ? 'Aucun run pour l\'instant'
                : `Aucun run avec le statut ${filter}`,
              MuiTablePagination: { labelRowsPerPage: 'Lignes par page' },
            }}
          />
        </Paper>
      )}
    </Box>
  );
}