import React, { useEffect, useState } from 'react';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import TextField from '@mui/material/TextField';
import MenuItem from '@mui/material/MenuItem';
import Alert from '@mui/material/Alert';
import Chip from '@mui/material/Chip';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import CircularProgress from '@mui/material/CircularProgress';
import { DataGrid } from '@mui/x-data-grid';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import BlockIcon from '@mui/icons-material/Block';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import { useAuth } from '../context/AuthContext';

const ROLES = ['VIEWER', 'QA_ENGINEER', 'QA_LEAD', 'ADMIN'];

const ROLE_COLORS = {
  ADMIN:       'error',
  QA_LEAD:     'warning',
  QA_ENGINEER: 'primary',
  VIEWER:      'default',
};

function RoleChip({ role }) {
  return (
    <Chip
      label={role}
      size="small"
      color={ROLE_COLORS[role] || 'default'}
      variant="outlined"
      sx={{ fontSize: 10, fontFamily: 'monospace' }}
    />
  );
}

export default function UsersPage() {
  const { token } = useAuth();
  const [users,   setUsers]   = useState([]);
  const [loading, setLoading] = useState(true);
  const [open,    setOpen]    = useState(false);
  const [editing, setEditing] = useState(null); // null = création

  const authHeader = { Authorization: `Bearer ${token}` };

  const load = () => {
    setLoading(true);
    fetch('/api/users', { headers: authHeader })
      .then(r => r.json())
      .then(data => setUsers(data))
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, []);

  const handleToggleActive = async (user) => {
    await fetch(`/api/users/${user.id}`, {
      method: 'PUT',
      headers: { ...authHeader, 'Content-Type': 'application/json' },
      body: JSON.stringify({ active: !user.active }),
    });
    load();
  };

  const handleDelete = async (id) => {
    if (!window.confirm('Supprimer cet utilisateur définitivement ?')) return;
    await fetch(`/api/users/${id}`, { method: 'DELETE', headers: authHeader });
    load();
  };

  const columns = [
    { field: 'fullName', headerName: 'Nom complet', flex: 1,
      renderCell: p => <Typography variant="body2" fontWeight={600}>{p.value}</Typography> },
    { field: 'username', headerName: 'Username', width: 140,
      renderCell: p => <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>{p.value}</Typography> },
    { field: 'email', headerName: 'Email', flex: 1,
      renderCell: p => <Typography variant="caption">{p.value}</Typography> },
    { field: 'role', headerName: 'Rôle', width: 130,
      renderCell: p => <RoleChip role={p.value} /> },
    { field: 'active', headerName: 'Statut', width: 100,
      renderCell: p => p.value
        ? <Chip label="Actif" size="small" color="success" sx={{ fontSize: 10 }} />
        : <Chip label="Inactif" size="small" color="default" sx={{ fontSize: 10 }} /> },
    { field: 'lastLoginAt', headerName: 'Dernier login', width: 140,
      renderCell: p => <Typography variant="caption">
        {p.value ? new Date(p.value).toLocaleString('fr-FR', {
          day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit'
        }) : '—'}
      </Typography> },
    { field: 'actions', headerName: 'Actions', width: 120, sortable: false,
      renderCell: p => (
        <Box sx={{ display: 'flex', gap: 0.5 }}>
          <Tooltip title="Modifier">
            <IconButton size="small" onClick={() => { setEditing(p.row); setOpen(true); }}>
              <EditIcon fontSize="small" />
            </IconButton>
          </Tooltip>
          <Tooltip title={p.row.active ? 'Désactiver' : 'Activer'}>
            <IconButton size="small"
              color={p.row.active ? 'warning' : 'success'}
              onClick={() => handleToggleActive(p.row)}>
              {p.row.active
                ? <BlockIcon fontSize="small" />
                : <CheckCircleIcon fontSize="small" />}
            </IconButton>
          </Tooltip>
          <Tooltip title="Supprimer">
            <IconButton size="small" color="error"
              onClick={() => handleDelete(p.row.id)}>
              <DeleteIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        </Box>
      )},
  ];

  return (
    <Box sx={{ p: 3, overflow: 'auto', flex: 1 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between',
        alignItems: 'center', mb: 3 }}>
        <Box>
          <Typography variant="h5">Utilisateurs</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
            {users.length} compte{users.length > 1 ? 's' : ''}
          </Typography>
        </Box>
        <Button variant="contained" startIcon={<AddIcon />}
          onClick={() => { setEditing(null); setOpen(true); }}>
          Nouveau utilisateur
        </Button>
      </Box>

      <Paper sx={{ height: 500 }}>
        <DataGrid
          rows={users}
          columns={columns}
          loading={loading}
          pageSize={10}
          rowsPerPageOptions={[10, 25]}
          disableSelectionOnClick
          sx={{ border: 'none' }}
          localeText={{ noRowsLabel: 'Aucun utilisateur' }}
        />
      </Paper>

      <UserDialog
        open={open}
        editing={editing}
        token={token}
        onClose={() => { setOpen(false); setEditing(null); }}
        onSaved={() => { setOpen(false); setEditing(null); load(); }}
      />
    </Box>
  );
}

// ── Dialog création / modification ────────────────────────────────────────

function UserDialog({ open, editing, token, onClose, onSaved }) {
  const isEdit = !!editing;

  const [fullName, setFullName] = useState('');
  const [username, setUsername] = useState('');
  const [email,    setEmail]    = useState('');
  const [role,     setRole]     = useState('QA_ENGINEER');
  const [password, setPassword] = useState('');
  const [loading,  setLoading]  = useState(false);
  const [error,    setError]    = useState('');

  useEffect(() => {
    if (editing) {
      setFullName(editing.fullName || '');
      setUsername(editing.username || '');
      setEmail(editing.email || '');
      setRole(editing.role || 'QA_ENGINEER');
      setPassword('');
    } else {
      setFullName(''); setUsername(''); setEmail('');
      setRole('QA_ENGINEER'); setPassword('');
    }
    setError('');
  }, [editing, open]);

  const handleSubmit = async () => {
    setLoading(true); setError('');
    try {
      const url    = isEdit ? `/api/users/${editing.id}` : '/api/users';
      const method = isEdit ? 'PUT' : 'POST';
      const body   = isEdit
        ? { fullName, email, role }
        : { fullName, username, email, role, password };

      const res = await fetch(url, {
        method,
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`,
        },
        body: JSON.stringify(body),
      });

      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.message || 'Erreur serveur');
      }
      onSaved();
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>
        {isEdit ? `Modifier — ${editing?.username}` : 'Nouvel utilisateur'}
      </DialogTitle>
      <DialogContent sx={{ pt: '8px !important' }}>
        <TextField label="Nom complet *" fullWidth margin="dense" size="small"
          value={fullName} onChange={e => setFullName(e.target.value)} autoFocus />
        {!isEdit && (
          <TextField label="Username *" fullWidth margin="dense" size="small"
            value={username} onChange={e => setUsername(e.target.value)} />
        )}
        <TextField label="Email *" fullWidth margin="dense" size="small"
          type="email" value={email} onChange={e => setEmail(e.target.value)} />
        <TextField select label="Rôle *" fullWidth margin="dense" size="small"
          value={role} onChange={e => setRole(e.target.value)}>
          {ROLES.map(r => (
            <MenuItem key={r} value={r}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <RoleChip role={r} />
                <Typography variant="caption" color="text.secondary">
                  {r === 'VIEWER'      && '— lecture seule'}
                  {r === 'QA_ENGINEER' && '— lancer tests'}
                  {r === 'QA_LEAD'     && '— gérer projets + lancer tests'}
                  {r === 'ADMIN'       && '— accès total'}
                </Typography>
              </Box>
            </MenuItem>
          ))}
        </TextField>
        {!isEdit && (
          <TextField label="Mot de passe initial *" fullWidth margin="dense"
            size="small" type="password" value={password}
            onChange={e => setPassword(e.target.value)}
            helperText="L'utilisateur devra le changer à sa première connexion" />
        )}
        {error && <Alert severity="error" sx={{ mt: 2, fontSize: 12 }}>{error}</Alert>}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Annuler</Button>
        <Button variant="contained" onClick={handleSubmit} disabled={loading}
          startIcon={loading ? <CircularProgress size={16} /> : null}>
          {loading ? 'Enregistrement…' : isEdit ? 'Modifier' : 'Créer'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}