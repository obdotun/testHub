import React, { useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import Box from '@mui/material/Box';
import Drawer from '@mui/material/Drawer';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Typography from '@mui/material/Typography';
import Divider from '@mui/material/Divider';
import Tooltip from '@mui/material/Tooltip';
import DashboardIcon from '@mui/icons-material/Dashboard';
import FolderIcon from '@mui/icons-material/Folder';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import BoltIcon from '@mui/icons-material/Bolt';
import StatusChip from './StatusChip';

const DRAWER_W = 240;

const NAV = [
  { path: '/',         label: 'Dashboard',    icon: <DashboardIcon fontSize="small" /> },
  { path: '/projects', label: 'Projets',       icon: <FolderIcon fontSize="small" />    },
  { path: '/runs',     label: 'Exécutions',    icon: <PlayArrowIcon fontSize="small" /> },
];

export default function AppLayout({ children, projects = [] }) {
  const navigate  = useNavigate();
  const location  = useLocation();

  const isActive = (path) =>
    path === '/' ? location.pathname === '/' : location.pathname.startsWith(path);

  return (
    <Box sx={{ display: 'flex', height: '100vh', bgcolor: 'background.default' }}>

      {/* ── Sidebar ─────────────────────────────────────────── */}
      <Drawer
        variant="permanent"
        sx={{
          width: DRAWER_W,
          flexShrink: 0,
          '& .MuiDrawer-paper': {
            width: DRAWER_W,
            boxSizing: 'border-box',
            bgcolor: 'background.paper',
            borderRight: '1px solid',
            borderColor: 'divider',
          },
        }}
      >
        {/* Logo */}
        <Box sx={{ px: 2, py: 2.5, borderBottom: '1px solid', borderColor: 'divider' }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <BoltIcon sx={{ color: 'primary.main', fontSize: 22 }} />
            <Box>
              <Typography sx={{
                fontFamily: '"JetBrains Mono", monospace',
                fontSize: 12, fontWeight: 700,
                color: 'primary.main', letterSpacing: '0.05em',
                textTransform: 'uppercase',
              }}>
                RF Platform
              </Typography>
              <Typography variant="caption" color="text.disabled">
                Robot Framework CI
              </Typography>
            </Box>
          </Box>
        </Box>

        {/* Navigation */}
        <List dense sx={{ px: 1, pt: 1 }}>
          <Typography variant="caption" sx={{
            px: 1.5, pb: 0.5, display: 'block',
            color: 'text.disabled', fontWeight: 600,
            textTransform: 'uppercase', letterSpacing: '0.08em', fontSize: 10,
          }}>
            Navigation
          </Typography>
          {NAV.map(({ path, label, icon }) => (
            <ListItemButton
              key={path}
              selected={isActive(path)}
              onClick={() => navigate(path)}
              sx={{
                borderRadius: 2, mb: 0.3,
                '&.Mui-selected': {
                  bgcolor: 'primary.main',
                  color: '#fff',
                  '& .MuiListItemIcon-root': { color: '#fff' },
                  '&:hover': { bgcolor: 'primary.dark' },
                },
              }}
            >
              <ListItemIcon sx={{ minWidth: 32, color: 'text.secondary' }}>
                {icon}
              </ListItemIcon>
              <ListItemText
                primary={label}
                primaryTypographyProps={{ fontSize: 13, fontWeight: 500 }}
              />
            </ListItemButton>
          ))}
        </List>

        {/* Projets rapides */}
        {projects.length > 0 && (
          <>
            <Divider sx={{ mx: 2, my: 1, borderColor: 'divider' }} />
            <List dense sx={{ px: 1 }}>
              <Typography variant="caption" sx={{
                px: 1.5, pb: 0.5, display: 'block',
                color: 'text.disabled', fontWeight: 600,
                textTransform: 'uppercase', letterSpacing: '0.08em', fontSize: 10,
              }}>
                Projets
              </Typography>
              {projects.map(p => (
                <Tooltip key={p.id} title={p.name} placement="right">
                  <ListItemButton
                    selected={location.pathname === `/projects/${p.id}`}
                    onClick={() => navigate(`/projects/${p.id}`)}
                    sx={{ borderRadius: 2, mb: 0.3 }}
                  >
                    <ListItemIcon sx={{ minWidth: 32 }}>
                      <FolderIcon fontSize="small" sx={{ color: 'secondary.main' }} />
                    </ListItemIcon>
                    <ListItemText
                      primary={p.name}
                      primaryTypographyProps={{
                        fontSize: 12, fontWeight: 500,
                        noWrap: true, color: 'text.secondary',
                      }}
                    />
                    <StatusChip status={p.venvStatus} size="small" />
                  </ListItemButton>
                </Tooltip>
              ))}
            </List>
          </>
        )}

        {/* Footer */}
        <Box sx={{ mt: 'auto', p: 2, borderTop: '1px solid', borderColor: 'divider' }}>
          <Typography variant="caption" color="text.disabled">
            Backend : localhost:8080
          </Typography>
        </Box>
      </Drawer>

      {/* ── Contenu principal ────────────────────────────────── */}
      <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        {children}
      </Box>
    </Box>
  );
}