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
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import { projectApi } from '../api/client';
import StatusChip from '../components/StatusChip';

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

  return (
    <Box sx={{ p: 3, overflow: 'auto', flex: 1 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 3 }}>
        <Box>
          <Typography variant="h5">Projets</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
            Chaque projet est un dépôt Robot Framework avec son venv Python isolé
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
            Aucun projet — importez votre premier ZIP Robot Framework
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
                {/* Header carte */}
                <Box sx={{ px: 2.5, py: 2, display: 'flex',
                  alignItems: 'center', justifyContent: 'space-between' }}>
                  <Typography variant="subtitle1" fontWeight={700} noWrap sx={{ flex: 1 }}>
                    {p.name}
                  </Typography>
                  <StatusChip status={p.venvStatus} />
                </Box>

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
                        color: s.color, fontFamily: '"JetBrains Mono", monospace',
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
                    {p.originalZipName}
                  </Typography>
                  <Button
                    size="small" color="error" startIcon={<DeleteIcon fontSize="small" />}
                    onClick={e => handleDelete(e, p.id)}
                    sx={{ ml: 1, fontSize: 11 }}
                  >
                    Supprimer
                  </Button>
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

function CreateProjectDialog({ open, onClose, onCreated }) {
  const [name,     setName]     = useState('');
  const [desc,     setDesc]     = useState('');
  const [testsDir, setTestsDir] = useState('Tests');
  const [file,     setFile]     = useState(null);
  const [loading,  setLoading]  = useState(false);
  const [error,    setError]    = useState('');
  const [drag,     setDrag]     = useState(false);
  const inputRef = useRef();

  const reset = () => { setName(''); setDesc(''); setTestsDir('Tests'); setFile(null); setError(''); };

  const handleClose = () => { reset(); onClose(); };

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
      await projectApi.create(name, desc, testsDir, file);
      reset(); onCreated();
    } catch (e) {
      setError(e.message || 'Erreur lors de la création');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <DialogTitle>Nouveau projet</DialogTitle>
      <DialogContent>
        <TextField
          label="Nom du projet *" fullWidth margin="normal" size="small"
          value={name} onChange={e => setName(e.target.value)}
          placeholder="eforex, einsurance…" autoFocus
        />
        <TextField
          label="Description" fullWidth margin="normal" size="small"
          value={desc} onChange={e => setDesc(e.target.value)}
        />
        <TextField
          label="Dossier des tests" fullWidth margin="normal" size="small"
          value={testsDir} onChange={e => setTestsDir(e.target.value)}
          helperText="Dossier contenant les fichiers .robot (défaut : Tests)"
        />

        {/* Zone upload */}
        <Box
          onDragOver={e => { e.preventDefault(); setDrag(true); }}
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
          }}
        >
          <CloudUploadIcon sx={{ fontSize: 36, color: 'text.disabled', mb: 1 }} />
          {file ? (
            <>
              <Typography variant="body2" fontWeight={600} color="primary.main">
                {file.name}
              </Typography>
              <Typography variant="caption" color="text.disabled">
                {(file.size / 1024 / 1024).toFixed(1)} Mo
              </Typography>
            </>
          ) : (
            <>
              <Typography variant="body2" color="text.secondary">
                Glissez votre ZIP ou <Typography component="span" color="primary.main">
                  cliquez pour parcourir
                </Typography>
              </Typography>
              <Typography variant="caption" color="text.disabled">
                Sans venv/ — max 50 Mo
              </Typography>
            </>
          )}
          <input ref={inputRef} type="file" accept=".zip"
            style={{ display: 'none' }}
            onChange={e => setFile(e.target.files[0])} />
        </Box>

        {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={handleClose}>Annuler</Button>
        <Button variant="contained" onClick={handleSubmit} disabled={loading}
          startIcon={loading ? <CircularProgress size={16} /> : null}>
          {loading ? 'Création…' : 'Créer le projet'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}