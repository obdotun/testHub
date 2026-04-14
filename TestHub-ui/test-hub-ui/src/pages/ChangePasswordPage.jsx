import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Alert from '@mui/material/Alert';
import CircularProgress from '@mui/material/CircularProgress';
import LockResetIcon from '@mui/icons-material/LockReset';
import { useAuth } from '../context/AuthContext';

export default function ChangePasswordPage() {
  const [current,  setCurrent]  = useState('');
  const [newPass,  setNewPass]  = useState('');
  const [confirm,  setConfirm]  = useState('');
  const [loading,  setLoading]  = useState(false);
  const [error,    setError]    = useState('');

  const { user, changePassword } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (newPass !== confirm) return setError('Les mots de passe ne correspondent pas');
    if (newPass.length < 8)  return setError('Minimum 8 caractères');

    setLoading(true); setError('');
    try {
      await changePassword(current, newPass);
      navigate('/');
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box sx={{
      minHeight: '100vh', display: 'flex',
      alignItems: 'center', justifyContent: 'center',
      bgcolor: '#0d0f14',
    }}>
      <Paper sx={{ p: 4, width: 400, maxWidth: '95vw' }}>
        <Box sx={{ textAlign: 'center', mb: 3 }}>
          <LockResetIcon sx={{ fontSize: 48, color: 'warning.main', mb: 1 }} />
          <Typography variant="h6" fontWeight={700}>
            Changement de mot de passe requis
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
            Bonjour <strong>{user?.fullName}</strong> — pour sécuriser votre compte,
            veuillez définir un nouveau mot de passe.
          </Typography>
        </Box>

        <Alert severity="info" sx={{ mb: 3, fontSize: 12 }}>
          Le mot de passe doit contenir au moins 8 caractères.
        </Alert>

        <form onSubmit={handleSubmit}>
          <TextField
            label="Mot de passe actuel" fullWidth margin="dense" size="small"
            type="password" value={current}
            onChange={e => setCurrent(e.target.value)} autoFocus
          />
          <TextField
            label="Nouveau mot de passe" fullWidth margin="dense" size="small"
            type="password" value={newPass}
            onChange={e => setNewPass(e.target.value)}
          />
          <TextField
            label="Confirmer le nouveau mot de passe"
            fullWidth margin="dense" size="small"
            type="password" value={confirm}
            onChange={e => setConfirm(e.target.value)}
            error={confirm.length > 0 && confirm !== newPass}
            helperText={confirm.length > 0 && confirm !== newPass
              ? 'Les mots de passe ne correspondent pas' : ''}
          />

          {error && (
            <Alert severity="error" sx={{ mt: 2, fontSize: 12 }}>{error}</Alert>
          )}

          <Button
            type="submit" variant="contained" fullWidth
            sx={{ mt: 3 }} color="warning"
            disabled={loading || !current || !newPass || !confirm}
            startIcon={loading ? <CircularProgress size={16} /> : null}
          >
            {loading ? 'Enregistrement…' : 'Enregistrer le nouveau mot de passe'}
          </Button>
        </form>
      </Paper>
    </Box>
  );
}