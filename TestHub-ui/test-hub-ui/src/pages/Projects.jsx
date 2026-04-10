import React, { useEffect, useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import Box from '@mui/material/Box';
import Grid from '@mui/material/Grid';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import TextField from '@mui/material/TextField';
import Alert from '@mui/material/Alert';
import CircularProgress from '@mui/material/CircularProgress';
import Divider from '@mui/material/Divider';
import Tabs from '@mui/material/Tabs';
import Tab from '@mui/material/Tab';
import InputAdornment from '@mui/material/InputAdornment';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import GitHubIcon from '@mui/icons-material/GitHub';
import VisibilityIcon from '@mui/icons-material/Visibility';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';
import RefreshIcon from '@mui/icons-material/Refresh';
import { projectApi } from '../api/client';
import StatusChip from '../components/StatusChip';
import GitForm from '../components/GitForm';

export default function Projects({ onProjectsChange }) {
  const [projects, setProjects] = useState([]);
  const [open,     setOpen]     = useState(false);
  const [loading,  setLoading]  = useState(true);
  const navigate = useNavigate();

  const load = () =>
    projectApi.getAll()
      .then(data => { setProjects(data); onProjectsChange?.(data); })
      .finally(() => setLoading(false));

  useEffect(() => { load(); }, []);

  const handleDelete = async (e, id) => {
    e.stopPropagation();
    if (!window.confirm('Supprimer ce projet et tous ses runs ?')) return;
    await projectApi.delete(id);
    load();
  };

  const handlePull = async (e, id) => {
    e.stopPropagation();
    const username    = window.prompt('Bitbucket username :');
    const appPassword = window.prompt('Bitbucket App Password :');
    if (!username || !appPassword) return;
    await projectApi.pull(id, username, appPassword);
    alert('Pull lancé — suivez les logs dans l\'onglet Setup venv du projet');
  };

  return (
    <Box sx={{ p: 3, overflow: 'auto', flex: 1 }}>
      <Box sx={{ display: 'flex', alignItems: 'center',
        justifyContent: 'space-between', mb: 3 }}>
        <Box>
          <Typography variant="h5">Projets</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
            Importez un ZIP ou clonez depuis Bitbucket
          </Typography>
        </Box>
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setOpen(true)}>
          Nouveau projet
        </Button>
      </Box>

      {loading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 8 }}>
          <CircularProgress />
        </Box>
      ) : projects.length === 0 ? (
        <Paper sx={{ p: 6, textAlign: 'center' }}>
          <Typography color="text.disabled" sx={{ mb: 2 }}>
            Aucun projet — importez un ZIP ou clonez depuis Bitbucket
          </Typography>
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setOpen(true)}>
            Créer un projet
          </Button>
        </Paper>
      ) : (
        <Grid container spacing={2}>
          {projects.map(p => (
            <Grid item xs={12} sm={6} md={4} key={p.id}>
              <Paper
                onClick={() => navigate(`/projects/${p.id}`)}
                sx={{
                  p: 0, cursor: 'pointer', overflow: 'hidden',
                  transition: 'border-color 0.15s',
                  '&:hover': { borderColor: 'primary.main' },
                }}
              >
                {/* Header */}
                <Box sx={{ px: 2.5, py: 2, display: 'flex',
                  alignItems: 'center', justifyContent: 'space-between' }}>
                  <Box sx={{ flex: 1, minWidth: 0 }}>
                    <Typography variant="subtitle1" fontWeight={700} noWrap>
                      {p.name}
                    </Typography>
                    {/* Badge source */}
                    <Typography variant="caption" sx={{
                      color: p.source === 'GIT' ? 'primary.main' : 'text.disabled',
                      fontFamily: 'monospace',
                    }}>
                      {p.source === 'GIT' ? '⎇ ' + (p.branch || 'main') : '📦 ZIP'}
                    </Typography>
                  </Box>
                  <StatusChip status={p.venvStatus} />
                </Box>

                {/* URL repo si GIT */}
                {p.source === 'GIT' && p.repositoryUrl && (
                  <Typography variant="caption" color="text.disabled"
                    sx={{ px: 2.5, pb: 1, display: 'block',
                      overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {p.repositoryUrl}
                  </Typography>
                )}

                {p.description && (
                  <Typography variant="caption" color="text.secondary"
                    sx={{ px: 2.5, pb: 1, display: 'block' }}>
                    {p.description}
                  </Typography>
                )}

                <Divider />

                {/* Stats */}
                <Grid container sx={{ px: 2.5, py: 1.5 }} spacing={1}>
                  {[
                    { label: 'Tests',  value: p.totalTestCases, color: 'info.main'    },
                    { label: 'Passed', value: p.passedRuns,     color: 'success.main' },
                    { label: 'Failed', value: p.failedRuns,     color: 'error.main'   },
                  ].map(s => (
                    <Grid item xs={4} key={s.label} sx={{ textAlign: 'center' }}>
                      <Typography variant="h6" sx={{
                        color: s.color,
                        fontFamily: '"JetBrains Mono", monospace',
                      }}>
                        {s.value ?? '—'}
                      </Typography>
                      <Typography variant="caption" color="text.disabled"
                        sx={{ textTransform: 'uppercase', fontSize: 10 }}>
                        {s.label}
                      </Typography>
                    </Grid>
                  ))}
                </Grid>

                <Divider />

                {/* Footer */}
                <Box sx={{ px: 2.5, py: 1.5, display: 'flex',
                  alignItems: 'center', justifyContent: 'space-between' }}>
                  <Typography variant="caption" color="text.disabled" noWrap sx={{ flex: 1 }}>
                    {p.source === 'GIT' && p.lastCommit
                      ? `commit ${p.lastCommit}`
                      : p.originalZipName || ''}
                  </Typography>
                  <Box sx={{ display: 'flex', gap: 0.5, ml: 1 }}>
                    {/* Bouton pull si GIT */}
                    {p.source === 'GIT' && (
                      <Tooltip title="git pull">
                        <IconButton size="small" color="primary"
                          onClick={e => handlePull(e, p.id)}>
                          <RefreshIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    )}
                    <Button size="small" color="error"
                      startIcon={<DeleteIcon fontSize="small" />}
                      onClick={e => handleDelete(e, p.id)}
                      sx={{ fontSize: 11 }}>
                      Supprimer
                    </Button>
                  </Box>
                </Box>
              </Paper>
            </Grid>
          ))}
        </Grid>
      )}

      <CreateProjectDialog
        open={open}
        onClose={() => setOpen(false)}
        onCreated={() => { setOpen(false); load(); }}
      />
    </Box>
  );
}

// ── Modal de création ──────────────────────────────────────────────────────

function CreateProjectDialog({ open, onClose, onCreated }) {
  const [tab, setTab] = useState(0); // 0 = ZIP, 1 = Bitbucket

  const handleClose = () => { setTab(0); onClose(); };

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <DialogTitle>Nouveau projet</DialogTitle>
      <DialogContent sx={{ pt: '8px !important' }}>
        <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2 }}>
          <Tab label="📦 Upload ZIP" />
          <Tab label="⎇ Bitbucket" />
        </Tabs>

        {tab === 0 && (
          <ZipForm onCreated={onCreated} onClose={handleClose} />
        )}
        {tab === 1 && (
          // ← Ici on utilise le composant importé au lieu du inline
          <GitForm onCreated={onCreated} onClose={handleClose} />
        )}
      </DialogContent>
    </Dialog>
  );
}

// ── Formulaire ZIP ─────────────────────────────────────────────────────────

function ZipForm({ onCreated, onClose }) {
  const [name,     setName]     = useState('');
  const [desc,     setDesc]     = useState('');
  const [testsDir, setTestsDir] = useState('Tests');
  const [file,     setFile]     = useState(null);
  const [loading,  setLoading]  = useState(false);
  const [error,    setError]    = useState('');
  const [drag,     setDrag]     = useState(false);
  const inputRef = useRef();

  const handleDrop = (e) => {
    e.preventDefault(); setDrag(false);
    const f = e.dataTransfer.files[0];
    if (f?.name.endsWith('.zip')) setFile(f);
  };

  const handleSubmit = async () => {
    if (!name.trim()) return setError('Le nom est obligatoire');
    if (!file)        return setError('Sélectionnez un fichier ZIP');
    setLoading(true); setError('');
    try {
      await projectApi.createFromZip(name, desc, testsDir, file);
      onCreated();
    } catch (e) {
      setError(e.message || 'Erreur lors de la création');
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <TextField label="Nom du projet *" fullWidth margin="dense" size="small"
        value={name} onChange={e => setName(e.target.value)} autoFocus />
      <TextField label="Description" fullWidth margin="dense" size="small"
        value={desc} onChange={e => setDesc(e.target.value)} />
      <TextField label="Dossier des tests" fullWidth margin="dense" size="small"
        value={testsDir} onChange={e => setTestsDir(e.target.value)}
        helperText="Dossier contenant les .robot (défaut : Tests)" />

      <Box onDragOver={e => { e.preventDefault(); setDrag(true); }}
        onDragLeave={() => setDrag(false)}
        onDrop={handleDrop}
        onClick={() => inputRef.current.click()}
        sx={{
          mt: 2, p: 3, border: '2px dashed',
          borderColor: drag ? 'primary.main' : 'divider',
          borderRadius: 2, textAlign: 'center', cursor: 'pointer',
          bgcolor: drag ? 'primary.dark' : 'transparent',
          transition: 'all 0.2s',
          '&:hover': { borderColor: 'primary.main', bgcolor: '#1c2030' },
        }}>
        <CloudUploadIcon sx={{ fontSize: 36, color: 'text.disabled', mb: 1 }} />
        {file ? (
          <Typography variant="body2" color="primary.main" fontWeight={600}>
            {file.name} — {(file.size / 1024 / 1024).toFixed(1)} Mo
          </Typography>
        ) : (
          <Typography variant="body2" color="text.secondary">
            Glissez votre ZIP ou{' '}
            <Typography component="span" color="primary.main">cliquez</Typography>
            <br />
            <Typography variant="caption" color="text.disabled">
              Sans venv/ — max 50 Mo
            </Typography>
          </Typography>
        )}
        <input ref={inputRef} type="file" accept=".zip"
          style={{ display: 'none' }}
          onChange={e => setFile(e.target.files[0])} />
      </Box>

      {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}

      <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 1, mt: 2 }}>
        <Button onClick={onClose}>Annuler</Button>
        <Button variant="contained" onClick={handleSubmit} disabled={loading}
          startIcon={loading ? <CircularProgress size={16} /> : null}>
          {loading ? 'Création…' : 'Créer'}
        </Button>
      </Box>
    </>
  );
}