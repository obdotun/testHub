import React, { useState } from 'react';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Alert from '@mui/material/Alert';
import CircularProgress from '@mui/material/CircularProgress';
import Divider from '@mui/material/Divider';
import Typography from '@mui/material/Typography';
import InputAdornment from '@mui/material/InputAdornment';
import IconButton from '@mui/material/IconButton';
import MenuItem from '@mui/material/MenuItem';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import GitHubIcon from '@mui/icons-material/GitHub';
import VisibilityIcon from '@mui/icons-material/Visibility';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';
import AccountTreeIcon from '@mui/icons-material/AccountTree';
import { projectApi } from '../api/client';

export default function GitForm({ onCreated, onClose }) {
  const [name,        setName]        = useState('');
  const [desc,        setDesc]        = useState('');
  const [repoUrl,     setRepoUrl]     = useState('');
  const [branch,      setBranch]      = useState('');
  const [branches,    setBranches]    = useState([]);   // liste chargée depuis Bitbucket
  const [testsDir,    setTestsDir]    = useState('Tests');
  const [username,    setUsername]    = useState('');
  const [appPassword, setAppPassword] = useState('');
  const [showPass,    setShowPass]    = useState(false);
  const [loading,     setLoading]     = useState(false);
  const [loadingBranches, setLoadingBranches] = useState(false);
  const [error,       setError]       = useState('');
  const [branchError, setBranchError] = useState('');

  // ── Charger les branches ────────────────────────────────────────────────
  const handleLoadBranches = async () => {
    if (!repoUrl.trim())    return setBranchError('L\'URL du repo est obligatoire');
    if (!username.trim())   return setBranchError('Le username est obligatoire');
    if (!appPassword.trim()) return setBranchError('L\'App Password est obligatoire');

    setLoadingBranches(true);
    setBranchError('');
    setBranches([]);
    setBranch('');

    try {
      const list = await projectApi.listBranches(repoUrl, username, appPassword);
      setBranches(list);
      // Sélectionner automatiquement "main" ou la première branche
      const defaultBranch = list.find(b => b === 'main' || b === 'master') || list[0];
      setBranch(defaultBranch || '');
    } catch (e) {
      setBranchError(e.message || 'Impossible de récupérer les branches');
    } finally {
      setLoadingBranches(false);
    }
  };

  // ── Créer le projet ─────────────────────────────────────────────────────
  const handleSubmit = async () => {
    if (!name.trim())    return setError('Le nom est obligatoire');
    if (!repoUrl.trim()) return setError('L\'URL du repo est obligatoire');
    if (!branch)         return setError('Sélectionnez une branche');
    if (!username.trim()) return setError('Le username est obligatoire');
    if (!appPassword.trim()) return setError('L\'App Password est obligatoire');

    setLoading(true); setError('');
    try {
      await projectApi.createFromGit({
        name, description: desc,
        repositoryUrl: repoUrl,
        branch, testsDir,
        username, appPassword,
      });
      onCreated();
    } catch (e) {
      setError(e.message || 'Erreur lors du clonage');
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <Alert severity="info" sx={{ mb: 2, fontSize: 12 }}>
        Les credentials ne sont <strong>jamais stockés</strong> en base.
        Ils sont utilisés uniquement pour le clone.
      </Alert>

      {/* ── Credentials ────────────────────────────────────────────────── */}
      <Typography variant="caption" color="text.disabled"
        sx={{ textTransform: 'uppercase', letterSpacing: '0.08em', fontWeight: 600 }}>
        Authentification Bitbucket
      </Typography>

      <TextField label="Username *" fullWidth margin="dense" size="small"
        value={username} onChange={e => setUsername(e.target.value)}
        placeholder="votre_username_bitbucket" />

      <TextField
        label="App Password *" fullWidth margin="dense" size="small"
        type={showPass ? 'text' : 'password'}
        value={appPassword} onChange={e => setAppPassword(e.target.value)}
        helperText="Bitbucket → Settings → App passwords → Read (Repositories)"
        InputProps={{
          endAdornment: (
            <InputAdornment position="end">
              <IconButton size="small" onClick={() => setShowPass(!showPass)}>
                {showPass
                  ? <VisibilityOffIcon fontSize="small" />
                  : <VisibilityIcon fontSize="small" />}
              </IconButton>
            </InputAdornment>
          ),
        }}
      />

      <Divider sx={{ my: 2 }} />

      {/* ── Repository ─────────────────────────────────────────────────── */}
      <Typography variant="caption" color="text.disabled"
        sx={{ textTransform: 'uppercase', letterSpacing: '0.08em', fontWeight: 600 }}>
        Repository
      </Typography>

      <TextField
        label="URL du repo *" fullWidth margin="dense" size="small"
        value={repoUrl} onChange={e => { setRepoUrl(e.target.value); setBranches([]); setBranch(''); }}
        placeholder="https://bitbucket.org/organisation/repo.git"
        InputProps={{
          startAdornment: (
            <InputAdornment position="start">
              <GitHubIcon fontSize="small" sx={{ color: 'text.disabled' }} />
            </InputAdornment>
          ),
        }}
      />

      {/* ── Bouton charger les branches ──────────────────────────────────── */}
      <Button
        variant="outlined"
        size="small"
        fullWidth
        startIcon={loadingBranches
          ? <CircularProgress size={14} />
          : <AccountTreeIcon fontSize="small" />}
        onClick={handleLoadBranches}
        disabled={loadingBranches || !repoUrl || !username || !appPassword}
        sx={{ mt: 1, mb: 1 }}
      >
        {loadingBranches ? 'Chargement des branches…' : 'Charger les branches'}
      </Button>

      {branchError && (
        <Alert severity="error" sx={{ mb: 1, fontSize: 12 }}>{branchError}</Alert>
      )}

      {/* ── Sélection de la branche ──────────────────────────────────────── */}
      {branches.length > 0 && (
        <>
          <Box sx={{ mb: 1, display: 'flex', alignItems: 'center', gap: 1 }}>
            <Typography variant="caption" color="success.main">
              ✔ {branches.length} branche{branches.length > 1 ? 's' : ''} trouvée{branches.length > 1 ? 's' : ''}
            </Typography>
            {/* Aperçu des branches sous forme de chips */}
            <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
              {branches.slice(0, 5).map(b => (
                <Chip
                  key={b}
                  label={b}
                  size="small"
                  variant={branch === b ? 'filled' : 'outlined'}
                  color={branch === b ? 'primary' : 'default'}
                  onClick={() => setBranch(b)}
                  sx={{ fontSize: 11, height: 20, cursor: 'pointer' }}
                />
              ))}
              {branches.length > 5 && (
                <Chip label={`+${branches.length - 5}`} size="small"
                  sx={{ fontSize: 11, height: 20 }} />
              )}
            </Box>
          </Box>

          <TextField
            select
            label="Branche *"
            fullWidth margin="dense" size="small"
            value={branch}
            onChange={e => setBranch(e.target.value)}
          >
            {branches.map(b => (
              <MenuItem key={b} value={b}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <AccountTreeIcon sx={{ fontSize: 14, color: 'text.disabled' }} />
                  {b}
                </Box>
              </MenuItem>
            ))}
          </TextField>
        </>
      )}

      <Divider sx={{ my: 2 }} />

      {/* ── Infos projet ─────────────────────────────────────────────────── */}
      <Typography variant="caption" color="text.disabled"
        sx={{ textTransform: 'uppercase', letterSpacing: '0.08em', fontWeight: 600 }}>
        Projet
      </Typography>

      <TextField label="Nom du projet *" fullWidth margin="dense" size="small"
        value={name} onChange={e => setName(e.target.value)} />
      <TextField label="Description" fullWidth margin="dense" size="small"
        value={desc} onChange={e => setDesc(e.target.value)} />
      <TextField label="Dossier des tests" fullWidth margin="dense" size="small"
        value={testsDir} onChange={e => setTestsDir(e.target.value)}
        helperText="Dossier contenant les .robot (défaut : Tests)" />

      {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}

      <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 1, mt: 2 }}>
        <Button onClick={onClose}>Annuler</Button>
        <Button variant="contained" onClick={handleSubmit}
          disabled={loading || !branch}
          startIcon={loading ? <CircularProgress size={16} /> : <GitHubIcon />}>
          {loading ? 'Clonage…' : 'Cloner et créer'}
        </Button>
      </Box>
    </>
  );
}