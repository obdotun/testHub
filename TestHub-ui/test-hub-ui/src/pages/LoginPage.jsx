import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Alert from '@mui/material/Alert';
import CircularProgress from '@mui/material/CircularProgress';
import InputAdornment from '@mui/material/InputAdornment';
import IconButton from '@mui/material/IconButton';
import VisibilityIcon from '@mui/icons-material/Visibility';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';
import BoltIcon from '@mui/icons-material/Bolt';
import { useAuth } from '../context/AuthContext';

export default function LoginPage() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPass, setShowPass] = useState(false);
  const [loading,  setLoading]  = useState(false);
  const [error,    setError]    = useState('');

  const { login } = useAuth();
  const navigate  = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!username.trim() || !password.trim()) return;
    setLoading(true); setError('');

    try {
      const user = await login(username, password);
      // Si premier login → forcer changement password
      if (user.firstLogin) {
        navigate('/change-password');
      } else {
        navigate('/');
      }
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
      <Paper sx={{ p: 4, width: 380, maxWidth: '95vw' }}>

        {/* Logo */}
        <Box sx={{ textAlign: 'center', mb: 4 }}>
          <Box sx={{
            display: 'inline-flex', alignItems: 'center',
            justifyContent: 'center', width: 56, height: 56,
            borderRadius: 2, bgcolor: 'primary.main', mb: 2,
          }}>
            <BoltIcon sx={{ fontSize: 32, color: 'white' }} />
          </Box>
          <Typography variant="h5" fontWeight={700}>TestHub</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
            Robot Framework CI Platform
          </Typography>
        </Box>

        <form onSubmit={handleSubmit}>
          <TextField
            label="Username" fullWidth margin="normal" size="small"
            value={username} onChange={e => setUsername(e.target.value)}
            autoFocus autoComplete="username"
          />
          <TextField
            label="Mot de passe" fullWidth margin="normal" size="small"
            type={showPass ? 'text' : 'password'}
            value={password} onChange={e => setPassword(e.target.value)}
            autoComplete="current-password"
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

          {error && (
            <Alert severity="error" sx={{ mt: 2, fontSize: 12 }}>{error}</Alert>
          )}

          <Button
            type="submit" variant="contained" fullWidth
            sx={{ mt: 3, py: 1.2 }}
            disabled={loading || !username || !password}
            startIcon={loading ? <CircularProgress size={16} /> : null}
          >
            {loading ? 'Connexion…' : 'Se connecter'}
          </Button>
        </form>

        {/*<Typography variant="caption" color="text.disabled"
          sx={{ display: 'block', textAlign: 'center', mt: 3 }}>
          Accès réservé — WebbFontaine QA Team
        </Typography>*/}
      </Paper>
    </Box>
  );
}