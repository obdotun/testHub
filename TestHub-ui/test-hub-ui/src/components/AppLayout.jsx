import React, { useState, useEffect } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import Box from '@mui/material/Box';
import Drawer from '@mui/material/Drawer';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Typography from '@mui/material/Typography';
import Divider from '@mui/material/Divider';
import Avatar from '@mui/material/Avatar';
import Chip from '@mui/material/Chip';
import Tooltip from '@mui/material/Tooltip';
import IconButton from '@mui/material/IconButton';
import DashboardIcon from '@mui/icons-material/Dashboard';
import FolderIcon from '@mui/icons-material/Folder';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import PeopleIcon from '@mui/icons-material/People';
import LogoutIcon from '@mui/icons-material/Logout';
import BoltIcon from '@mui/icons-material/Bolt';
import { useAuth } from '../context/AuthContext';
import { projectApi } from '../api/client';

const DRAWER_WIDTH = 240;

const ROLE_COLORS = {
  ADMIN:       '#ef4444',
  QA_LEAD:     '#f59e0b',
  QA_ENGINEER: '#6366f1',
  VIEWER:      '#6b7280',
};

export default function AppLayout() {
  const { user, logout, isAdmin, isQaLead } = useAuth();
  const navigate  = useNavigate();
  const location  = useLocation();
  const [projects, setProjects] = useState([]);

  useEffect(() => {
    projectApi.getAll().then(setProjects).catch(() => {});
  }, [location.pathname]);

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  // Menu adapté selon le rôle
  const navItems = [
    { label: 'Dashboard',   path: '/dashboard', icon: <DashboardIcon /> },
    { label: 'Projets',     path: '/projects',  icon: <FolderIcon /> },
    { label: 'Exécutions',  path: '/runs',      icon: <PlayArrowIcon /> },
    // Utilisateurs — ADMIN seulement
    ...(isAdmin() ? [{ label: 'Utilisateurs', path: '/users', icon: <PeopleIcon /> }] : []),
  ];

  const isActive = (path) => location.pathname.startsWith(path);

  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      {/* Drawer */}
      <Drawer
        variant="permanent"
        sx={{
          width: DRAWER_WIDTH,
          '& .MuiDrawer-paper': {
            width: DRAWER_WIDTH,
            bgcolor: '#111318',
            borderRight: '1px solid #1e2130',
            display: 'flex', flexDirection: 'column',
          },
        }}
      >
        {/* Logo */}
        <Box sx={{ px: 2.5, py: 2.5, display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <Box sx={{
            width: 32, height: 32, borderRadius: 1,
            bgcolor: 'primary.main',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <BoltIcon sx={{ fontSize: 20, color: 'white' }} />
          </Box>
          <Box>
            <Typography variant="subtitle1" fontWeight={700} lineHeight={1}>
              RF PLATFORM
            </Typography>
            <Typography variant="caption" color="text.disabled">
              Robot Framework CI
            </Typography>
          </Box>
        </Box>

        <Divider sx={{ borderColor: '#1e2130' }} />

        {/* Navigation */}
        <Box sx={{ px: 1.5, pt: 2 }}>
          <Typography variant="caption" color="text.disabled"
            sx={{ px: 1, textTransform: 'uppercase',
              letterSpacing: '0.1em', fontWeight: 600, fontSize: 10 }}>
            Navigation
          </Typography>
          <List dense sx={{ mt: 0.5 }}>
            {navItems.map(item => (
              <ListItem key={item.path} disablePadding sx={{ mb: 0.5 }}>
                <ListItemButton
                  onClick={() => navigate(item.path)}
                  selected={isActive(item.path)}
                  sx={{
                    borderRadius: 1.5,
                    '&.Mui-selected': {
                      bgcolor: 'primary.dark',
                      '& .MuiListItemIcon-root': { color: 'primary.light' },
                      '& .MuiListItemText-primary': { color: 'primary.light', fontWeight: 700 },
                    },
                  }}
                >
                  <ListItemIcon sx={{ minWidth: 36, color: 'text.disabled' }}>
                    {item.icon}
                  </ListItemIcon>
                  <ListItemText
                    primary={item.label}
                    primaryTypographyProps={{ variant: 'body2' }}
                  />
                </ListItemButton>
              </ListItem>
            ))}
          </List>
        </Box>

        {/* Projets récents */}
        {projects.length > 0 && (
          <Box sx={{ px: 1.5, pt: 2 }}>
            <Typography variant="caption" color="text.disabled"
              sx={{ px: 1, textTransform: 'uppercase',
                letterSpacing: '0.1em', fontWeight: 600, fontSize: 10 }}>
              Projets
            </Typography>
            <List dense sx={{ mt: 0.5 }}>
              {projects.slice(0, 6).map(p => (
                <ListItem key={p.id} disablePadding sx={{ mb: 0.3 }}>
                  <ListItemButton
                    onClick={() => navigate(`/projects/${p.id}`)}
                    selected={location.pathname === `/projects/${p.id}`}
                    sx={{ borderRadius: 1.5, py: 0.5 }}
                  >
                    <ListItemIcon sx={{ minWidth: 28 }}>
                      <FolderIcon sx={{ fontSize: 16, color: 'text.disabled' }} />
                    </ListItemIcon>
                    <ListItemText
                      primary={p.name}
                      primaryTypographyProps={{
                        variant: 'caption', noWrap: true,
                      }}
                    />
                    <Chip
                      label={p.venvStatus === 'READY' ? '●' : '○'}
                      size="small"
                      sx={{
                        height: 16, fontSize: 10, minWidth: 16,
                        bgcolor: 'transparent',
                        color: p.venvStatus === 'READY'
                          ? 'success.main' : 'text.disabled',
                      }}
                    />
                  </ListItemButton>
                </ListItem>
              ))}
            </List>
          </Box>
        )}

        {/* Spacer */}
        <Box sx={{ flex: 1 }} />

        <Divider sx={{ borderColor: '#1e2130' }} />

        {/* Profil utilisateur + logout */}
        <Box sx={{ px: 2, py: 1.5, display: 'flex',
          alignItems: 'center', gap: 1.5 }}>
          <Avatar sx={{
            width: 32, height: 32, fontSize: 13, fontWeight: 700,
            bgcolor: ROLE_COLORS[user?.role] || '#6366f1',
          }}>
            {user?.fullName?.charAt(0)?.toUpperCase()}
          </Avatar>
          <Box sx={{ flex: 1, minWidth: 0 }}>
            <Typography variant="caption" fontWeight={600} noWrap display="block">
              {user?.fullName}
            </Typography>
            <Typography variant="caption" color="text.disabled"
              sx={{ fontSize: 10, fontFamily: 'monospace' }}>
              {user?.role}
            </Typography>
          </Box>
          <Tooltip title="Se déconnecter">
            <IconButton size="small" onClick={handleLogout} sx={{ color: 'text.disabled' }}>
              <LogoutIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        </Box>

        {/* Indicateur backend */}
        <Typography variant="caption" color="text.disabled"
          sx={{ px: 2, pb: 1.5, fontSize: 10, fontFamily: 'monospace' }}>
          Backend : localhost:8080
        </Typography>
      </Drawer>

      {/* Contenu principal */}
      <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column',
        overflow: 'hidden', bgcolor: '#0d0f14' }}>
        <Outlet />
      </Box>
    </Box>
  );
}