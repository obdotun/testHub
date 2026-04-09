import { createTheme } from '@mui/material/styles';

const theme = createTheme({
  palette: {
    mode: 'dark',
    primary: {
      main: '#6366f1',       // indigo
      light: '#818cf8',
      dark: '#4f46e5',
    },
    secondary: {
      main: '#f97316',       // orange
    },
    success: {
      main: '#22c55e',
    },
    error: {
      main: '#ef4444',
    },
    warning: {
      main: '#eab308',
    },
    info: {
      main: '#3b82f6',
    },
    background: {
      default: '#0d0f14',
      paper:   '#141720',
    },
    divider: '#2a2f45',
    text: {
      primary:   '#e8eaf0',
      secondary: '#8b92b0',
      disabled:  '#4a5070',
    },
  },

  typography: {
    fontFamily: '"Inter", "Roboto", "Helvetica", "Arial", sans-serif',
    fontSize: 13,
    h4: { fontWeight: 700 },
    h5: { fontWeight: 700 },
    h6: { fontWeight: 600 },
    subtitle1: { fontWeight: 600 },
    subtitle2: { fontWeight: 600 },
  },

  shape: {
    borderRadius: 10,
  },

  components: {
    MuiCssBaseline: {
      styleOverrides: {
        body: {
          scrollbarWidth: 'thin',
          '&::-webkit-scrollbar': { width: 6 },
          '&::-webkit-scrollbar-thumb': {
            background: '#2a2f45',
            borderRadius: 3,
          },
        },
      },
    },

    MuiPaper: {
      styleOverrides: {
        root: {
          backgroundImage: 'none',
          border: '1px solid #2a2f45',
        },
      },
    },

    MuiButton: {
      styleOverrides: {
        root: {
          textTransform: 'none',
          fontWeight: 600,
          borderRadius: 8,
        },
      },
    },

    MuiChip: {
      styleOverrides: {
        root: {
          fontWeight: 600,
          fontSize: 11,
          height: 22,
          fontFamily: '"JetBrains Mono", monospace',
          letterSpacing: '0.04em',
        },
      },
    },

    MuiTableCell: {
      styleOverrides: {
        head: {
          fontWeight: 600,
          fontSize: 11,
          textTransform: 'uppercase',
          letterSpacing: '0.06em',
          color: '#4a5070',
          borderBottom: '1px solid #2a2f45',
          background: '#141720',
        },
        body: {
          borderBottom: '1px solid #1c2030',
          fontSize: 13,
        },
      },
    },

    MuiDataGrid: {
      styleOverrides: {
        root: {
          border: 'none',
          '& .MuiDataGrid-columnHeaders': {
            background: '#141720',
            borderBottom: '1px solid #2a2f45',
          },
          '& .MuiDataGrid-cell': {
            borderBottom: '1px solid #1c2030',
          },
          '& .MuiDataGrid-row:hover': {
            background: '#222840',
            cursor: 'pointer',
          },
          '& .MuiDataGrid-footerContainer': {
            borderTop: '1px solid #2a2f45',
          },
        },
      },
    },

    MuiTab: {
      styleOverrides: {
        root: {
          textTransform: 'none',
          fontWeight: 600,
          fontSize: 13,
          minHeight: 42,
        },
      },
    },

    MuiLinearProgress: {
      styleOverrides: {
        root: { borderRadius: 4 },
      },
    },

    MuiAlert: {
      styleOverrides: {
        root: { borderRadius: 8 },
      },
    },
  },
});

export default theme;