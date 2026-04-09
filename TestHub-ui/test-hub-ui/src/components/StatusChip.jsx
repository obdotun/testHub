import React from 'react';
import Chip from '@mui/material/Chip';
import CircleIcon from '@mui/icons-material/Circle';

const STATUS_CFG = {
  PASSED:     { label: 'Passed',     color: 'success' },
  FAILED:     { label: 'Failed',     color: 'error'   },
  RUNNING:    { label: 'Running',    color: 'warning'  },
  PENDING:    { label: 'Pending',    color: 'info'     },
  ERROR:      { label: 'Error',      color: 'error'    },
  CANCELLED:  { label: 'Cancelled',  color: 'default'  },
  READY:      { label: 'Ready',      color: 'success'  },
  INSTALLING: { label: 'Installing', color: 'warning'  },
  NONE:       { label: 'No venv',    color: 'default'  },
};

export default function StatusChip({ status, size = 'small' }) {
  const cfg = STATUS_CFG[status] || { label: status, color: 'default' };
  const isAnimated = status === 'RUNNING' || status === 'INSTALLING';

  return (
    <Chip
      size={size}
      label={cfg.label}
      color={cfg.color}
      variant="outlined"
      icon={isAnimated ? (
        <CircleIcon sx={{
          fontSize: '8px !important',
          animation: 'pulse 1.4s ease-in-out infinite',
          '@keyframes pulse': {
            '0%, 100%': { opacity: 1 },
            '50%':       { opacity: 0.3 },
          },
        }} />
      ) : undefined}
    />
  );
}