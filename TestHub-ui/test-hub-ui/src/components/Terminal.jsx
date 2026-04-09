import React, { useEffect, useRef } from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';

const LEVEL_COLOR = {
  INFO:    '#a8b4d0',
  SUCCESS: '#22c55e',
  ERROR:   '#ef4444',
  WARN:    '#eab308',
  SYSTEM:  '#818cf8',
};

function formatTime(ts) {
  if (!ts) return '';
  return new Date(ts).toLocaleTimeString('fr-FR', { hour12: false });
}

export default function Terminal({ logs = [], height = 480, title = 'Logs' }) {
  const bottomRef = useRef(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [logs]);

  return (
    <Box sx={{
      border: '1px solid',
      borderColor: 'divider',
      borderRadius: 2,
      overflow: 'hidden',
      height,
      display: 'flex',
      flexDirection: 'column',
      background: '#08090c',
    }}>
      {/* Barre titre style macOS */}
      <Box sx={{
        display: 'flex', alignItems: 'center', gap: 1,
        px: 2, py: 1,
        background: 'background.paper',
        borderBottom: '1px solid',
        borderColor: 'divider',
        bgcolor: '#1c2030',
      }}>
        {['#ef4444', '#eab308', '#22c55e'].map(c => (
          <Box key={c} sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: c }} />
        ))}
        <Typography variant="caption" sx={{
          ml: 1, color: 'text.disabled',
          fontFamily: '"JetBrains Mono", monospace',
        }}>
          {title}
        </Typography>
      </Box>

      {/* Corps */}
      <Box sx={{ flex: 1, overflowY: 'auto', p: 1.5,
        '&::-webkit-scrollbar': { width: 4 },
        '&::-webkit-scrollbar-thumb': { background: '#2a2f45', borderRadius: 2 },
      }}>
        {logs.length === 0 && (
          <Typography variant="caption" sx={{
            color: 'text.disabled',
            fontFamily: '"JetBrains Mono", monospace',
          }}>
            En attente de logs…
          </Typography>
        )}

        {logs.map((log, i) => (
          <Box key={i} sx={{ display: 'flex', gap: 1.5, mb: 0.3 }}>
            <Typography component="span" sx={{
              fontSize: 11, color: '#4a5070', flexShrink: 0,
              fontFamily: '"JetBrains Mono", monospace',
            }}>
              {formatTime(log.timestamp)}
            </Typography>
            <Typography component="span" sx={{
              fontSize: 11.5,
              color: LEVEL_COLOR[log.level] || LEVEL_COLOR.INFO,
              fontFamily: '"JetBrains Mono", monospace',
              wordBreak: 'break-all',
              lineHeight: 1.6,
            }}>
              {log.text}
            </Typography>
          </Box>
        ))}
        <div ref={bottomRef} />
      </Box>
    </Box>
  );
}