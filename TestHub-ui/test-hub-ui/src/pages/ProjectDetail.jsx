import React, { useEffect, useState, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import Box from '@mui/material/Box';
import Grid from '@mui/material/Grid';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import Tabs from '@mui/material/Tabs';
import Tab from '@mui/material/Tab';
import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';
import Alert from '@mui/material/Alert';
import CircularProgress from '@mui/material/CircularProgress';
import Divider from '@mui/material/Divider';
import Tooltip from '@mui/material/Tooltip';
import IconButton from '@mui/material/IconButton';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import RefreshIcon from '@mui/icons-material/Refresh';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import ArticleIcon from '@mui/icons-material/Article';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import { DataGrid } from '@mui/x-data-grid';
import { projectApi, runApi } from '../api/client';
import StatusChip from '../components/StatusChip';
import SetupVenvTab from '../components/SetupVenvTab';

function fmtDate(dt) {
  if (!dt) return '—';
  return new Date(dt).toLocaleString('fr-FR', {
    day: '2-digit', month: '2-digit',
    hour: '2-digit', minute: '2-digit',
  });
}

export default function ProjectDetail() {
  const { id }   = useParams();
  const navigate = useNavigate();

  const [project,      setProject]      = useState(null);
  const [tree,         setTree]         = useState(null);
  const [runs,         setRuns]         = useState([]);
  const [tab,          setTab]          = useState(0);
  const [selected,     setSelected]     = useState(null);
  const [expanded,     setExpanded]     = useState({});
  const [launching,    setLaunching]    = useState(false);
  const [reinstalling, setReinstalling] = useState(false);

  const load = useCallback(() => {
    Promise.all([
      projectApi.getById(id),
      projectApi.getFiles(id),
      runApi.getByProject(id),
    ]).then(([p, t, r]) => {
      setProject(p); setTree(t); setRuns(r);
      if (p.venvStatus === 'INSTALLING') setTab(2);
    });
  }, [id]);

  useEffect(() => { load(); }, [load]);

  // Polling si venv en cours d'installation
  useEffect(() => {
    if (project?.venvStatus !== 'INSTALLING') return;
    const t = setInterval(load, 3000);
    return () => clearInterval(t);
  }, [project?.venvStatus, load]);

  const handleLaunch = async () => {
    setLaunching(true);
    try {
      let target, mode, label;
      if (selected?.testCase) {
        mode   = 'SINGLE_TEST';
        target = `${selected.file.relativePath}::${selected.testCase.name}`;
        label  = selected.testCase.name;
      } else if (selected?.file) {
        mode   = 'SUITE';
        target = selected.file.relativePath;
        label  = selected.file.suiteName;
      } else {
        mode   = 'SUITE';
        target = `${project.testsDir}/`;
        label  = `Suite complète — ${project.name}`;
      }
      const run = await runApi.launch({ projectId: Number(id), mode, target, label });
      navigate(`/runs/${run.id}`);
    } catch (e) {
      alert(e.message);
    } finally {
      setLaunching(false);
    }
  };

  // Partagé entre l'IconButton du header ET le bouton dans SetupVenvTab
  const handleReinstall = async () => {
    setReinstalling(true);
    try {
      await projectApi.reinstallVenv(id);
      setTab(2);
      load();
    } catch (e) {
      console.error('Erreur réinstallation venv:', e);
    } finally {
      setReinstalling(false);
    }
  };

  const toggle = (path) => setExpanded(p => ({ ...p, [path]: !p[path] }));

  const runColumns = [
    { field: 'id', headerName: '#', width: 60,
      renderCell: p => <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>#{p.value}</Typography> },
    { field: 'label', headerName: 'Label', flex: 1,
      renderCell: p => <Typography variant="body2" fontWeight={600}>{p.value}</Typography> },
    { field: 'mode', headerName: 'Mode', width: 110,
      renderCell: p => <Chip label={p.value} size="small" variant="outlined" sx={{ fontSize: 10 }} /> },
    { field: 'status', headerName: 'Statut', width: 120,
      renderCell: p => <StatusChip status={p.value} /> },
    { field: 'passed', headerName: 'Passés', width: 80,
      renderCell: p => <Typography sx={{ color: 'success.main', fontFamily: 'monospace' }}>{p.value ?? '—'}</Typography> },
    { field: 'failed', headerName: 'Échoués', width: 90,
      renderCell: p => <Typography sx={{ color: 'error.main', fontFamily: 'monospace' }}>{p.value ?? '—'}</Typography> },
    { field: 'durationSeconds', headerName: 'Durée', width: 90,
      renderCell: p => <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>{p.value ? `${p.value}s` : '—'}</Typography> },
    { field: 'startedAt', headerName: 'Date', width: 130,
      renderCell: p => <Typography variant="caption">{fmtDate(p.value)}</Typography> },
  ];

  if (!project) return (
    <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%' }}>
      <CircularProgress />
    </Box>
  );

  return (
    <Box sx={{ p: 3, overflow: 'auto', flex: 1 }}>
      {/* Header */}
      <Box sx={{ display: 'flex', alignItems: 'flex-start',
        justifyContent: 'space-between', mb: 3 }}>
        <Box>
          <Button size="small" startIcon={<ArrowBackIcon />}
            onClick={() => navigate('/projects')} sx={{ mb: 1 }}>
            Projets
          </Button>
          <Stack direction="row" spacing={1} alignItems="center">
            <Typography variant="h5">{project.name}</Typography>
            <StatusChip status={project.venvStatus} />
            {project.usesPabot && (
              <Chip label="pabot" size="small" color="primary" variant="outlined" />
            )}
          </Stack>
          {project.description && (
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
              {project.description}
            </Typography>
          )}
        </Box>
        <Stack direction="row" spacing={1}>
          {/* IconButton refresh — disabled + spinner pendant la réinstallation */}
          <Tooltip title="Réinstaller le venv">
            <span>
              <IconButton onClick={handleReinstall} disabled={reinstalling}>
                {reinstalling
                  ? <CircularProgress size={20} />
                  : <RefreshIcon />}
              </IconButton>
            </span>
          </Tooltip>
          <Button
            variant="contained" startIcon={<PlayArrowIcon />}
            onClick={handleLaunch}
            disabled={launching || project.venvStatus !== 'READY'}
          >
            {launching ? <CircularProgress size={16} sx={{ mr: 1 }} /> : null}
            {selected?.testCase ? selected.testCase.name
              : selected?.file   ? selected.file.suiteName
              : 'Lancer tout'}
          </Button>
        </Stack>
      </Box>

      {/* Stats */}
      <Grid container spacing={2} sx={{ mb: 3 }}>
        {[
          { label: 'Fichiers',  value: tree?.totalFiles ?? '—',     color: 'info.main'    },
          { label: 'Tests',     value: tree?.totalTestCases ?? '—', color: 'text.primary' },
          { label: 'Runs OK',   value: project.passedRuns,          color: 'success.main' },
          { label: 'Runs KO',   value: project.failedRuns,          color: 'error.main'   },
        ].map(s => (
          <Grid item xs={6} sm={3} key={s.label}>
            <Paper sx={{ p: 2 }}>
              <Typography variant="caption" color="text.disabled"
                sx={{ textTransform: 'uppercase', letterSpacing: '0.08em', fontWeight: 600 }}>
                {s.label}
              </Typography>
              <Typography variant="h5" sx={{
                color: s.color, fontFamily: '"JetBrains Mono", monospace', mt: 0.5 }}>
                {s.value}
              </Typography>
            </Paper>
          </Grid>
        ))}
      </Grid>

      {/* Tabs */}
      <Paper sx={{ mb: 2, p: 0 }}>
        <Tabs value={tab} onChange={(_, v) => setTab(v)}
          sx={{ borderBottom: '1px solid', borderColor: 'divider', px: 2 }}>
          <Tab label="📄 Fichiers & Tests" />
          <Tab label={`▶ Runs (${runs.length})`} />
          <Tab label="⚙ Setup venv" />
        </Tabs>
      </Paper>

      {/* ── Fichiers ─────────────────────────────────────────── */}
      {tab === 0 && (
        <Grid container spacing={2}>
          <Grid item xs={12} md={8}>
            <Paper sx={{ p: 0, overflow: 'hidden' }}>
              <Box sx={{ px: 2.5, py: 1.5, display: 'flex',
                alignItems: 'center', justifyContent: 'space-between' }}>
                <Typography variant="subtitle2">
                  {project.testsDir}/ — {tree?.totalFiles ?? 0} fichiers
                </Typography>
                <Button size="small" onClick={() => setSelected(null)}>
                  Tout sélectionner
                </Button>
              </Box>
              <Divider />
              <Box sx={{ maxHeight: 520, overflowY: 'auto' }}>
                {!tree || tree.files.length === 0 ? (
                  <Box sx={{ p: 4, textAlign: 'center' }}>
                    <Typography color="text.disabled">
                      Aucun fichier .robot dans {project.testsDir}/
                    </Typography>
                  </Box>
                ) : tree.files.map(file => (
                  <Box key={file.relativePath}>
                    <Box
                      onClick={() => {
                        toggle(file.relativePath);
                        setSelected({ file, testCase: null });
                      }}
                      sx={{
                        display: 'flex', alignItems: 'center', gap: 1,
                        px: 2, py: 1, cursor: 'pointer',
                        bgcolor: selected?.file?.relativePath === file.relativePath
                          && !selected?.testCase ? '#1c2030' : 'transparent',
                        '&:hover': { bgcolor: '#1c2030' }, transition: 'background 0.1s',
                      }}
                    >
                      {expanded[file.relativePath]
                        ? <ExpandMoreIcon fontSize="small" sx={{ color: 'text.disabled' }} />
                        : <ChevronRightIcon fontSize="small" sx={{ color: 'text.disabled' }} />
                      }
                      <ArticleIcon fontSize="small" sx={{ color: 'secondary.main' }} />
                      <Typography variant="body2" sx={{ flex: 1 }}>
                        {file.relativePath}
                      </Typography>
                      <Chip label={`${file.testCases.length} tests`}
                        size="small" variant="outlined"
                        sx={{ fontSize: 10, height: 20 }} />
                    </Box>

                    {expanded[file.relativePath] && file.testCases.map(tc => (
                      <Box
                        key={tc.name}
                        onClick={e => { e.stopPropagation(); setSelected({ file, testCase: tc }); }}
                        sx={{
                          display: 'flex', alignItems: 'center',
                          justifyContent: 'space-between',
                          pl: 6, pr: 2, py: 0.7, cursor: 'pointer',
                          bgcolor: selected?.testCase?.name === tc.name
                            ? 'primary.dark' : 'transparent',
                          '&:hover': { bgcolor: '#222840' }, transition: 'background 0.1s',
                        }}
                      >
                        <Typography variant="caption" color="text.secondary">
                          ◦ {tc.name}
                        </Typography>
                        {tc.tags && (
                          <Stack direction="row" spacing={0.5}>
                            {tc.tags.split(',').map(tag => (
                              <Chip key={tag} label={tag.trim()} size="small"
                                sx={{ height: 16, fontSize: 9 }} />
                            ))}
                          </Stack>
                        )}
                      </Box>
                    ))}
                    <Divider />
                  </Box>
                ))}
              </Box>
            </Paper>
          </Grid>

          {/* Panneau sélection */}
          <Grid item xs={12} md={4}>
            <Paper sx={{ p: 2.5 }}>
              <Typography variant="subtitle2" sx={{ mb: 2 }}>Sélection</Typography>
              {!selected ? (
                <Alert severity="info" sx={{ fontSize: 12 }}>
                  Aucune sélection — toute la suite sera exécutée
                </Alert>
              ) : (
                <Box>
                  <Typography variant="caption" color="text.disabled">Fichier</Typography>
                  <Typography variant="body2" sx={{
                    fontFamily: 'monospace', color: 'secondary.main',
                    wordBreak: 'break-all', mb: 1.5, mt: 0.5,
                  }}>
                    {selected.file.relativePath}
                  </Typography>
                  {selected.testCase && (
                    <>
                      <Typography variant="caption" color="text.disabled">Test case</Typography>
                      <Typography variant="body2" fontWeight={600} sx={{ mt: 0.5, mb: 1.5 }}>
                        {selected.testCase.name}
                      </Typography>
                    </>
                  )}
                  <Button size="small" fullWidth variant="outlined"
                    onClick={() => setSelected(null)}>
                    Effacer la sélection
                  </Button>
                </Box>
              )}
              <Button
                variant="contained" fullWidth startIcon={<PlayArrowIcon />}
                onClick={handleLaunch} sx={{ mt: 2 }}
                disabled={launching || project.venvStatus !== 'READY'}
              >
                {launching ? <CircularProgress size={16} sx={{ mr: 1 }} /> : null}
                Lancer
              </Button>
              {project.venvStatus !== 'READY' && (
                <Alert severity="warning" sx={{ mt: 2, fontSize: 12 }}>
                  Venv non prêt ({project.venvStatus})
                </Alert>
              )}
            </Paper>
          </Grid>
        </Grid>
      )}

      {/* ── Runs ─────────────────────────────────────────────── */}
      {tab === 1 && (
        <Paper sx={{ height: 500 }}>
          <DataGrid
            rows={runs}
            columns={runColumns}
            pageSize={10}
            rowsPerPageOptions={[10, 25, 50]}
            disableSelectionOnClick
            onRowClick={p => navigate(`/runs/${p.id}`)}
            sx={{ border: 'none' }}
            localeText={{
              noRowsLabel: 'Aucun run pour ce projet',
              MuiTablePagination: { labelRowsPerPage: 'Lignes par page' },
            }}
          />
        </Paper>
      )}

      {/* ── Setup venv ───────────────────────────────────────── */}
      {tab === 2 && (
        <SetupVenvTab
          project={project}
          projectId={id}
          onReinstall={handleReinstall}
        />
      )}
    </Box>
  );
}