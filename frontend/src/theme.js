import { createTheme } from '@mui/material/styles';

/**
 * The single place the storefront's look is defined.
 *
 * <p>Material components carry their own opinions, so the theme is where a shop
 * gets an identity rather than looking like every other Material app: a teal
 * brand colour, amber for the buy actions so they read as the primary thing on
 * a product card, softer corners, and flat surfaces with a hairline border
 * instead of Material's default shadows, which stack badly in a dense grid.
 */
const theme = createTheme({
  palette: {
    primary: { main: '#00695c', light: '#439889', dark: '#003d33' },
    secondary: { main: '#ffb300', dark: '#c68400', contrastText: '#1a1a1a' },
    error: { main: '#c62828' },
    success: { main: '#2e7d32' },
    background: { default: '#f4f6f8', paper: '#ffffff' },
    text: { primary: '#14181d', secondary: '#5b6470' },
    divider: '#e3e7ec',
  },

  shape: { borderRadius: 10 },

  typography: {
    fontFamily: '"Roboto", "Helvetica", "Arial", sans-serif',
    h1: { fontSize: '2rem', fontWeight: 500 },
    h2: { fontSize: '1.5rem', fontWeight: 500 },
    h3: { fontSize: '1.25rem', fontWeight: 500 },
    h6: { fontWeight: 500 },
    button: { textTransform: 'none', fontWeight: 500 },
  },

  components: {
    MuiPaper: {
      styleOverrides: {
        // A grid of forty cards with drop shadows looks like noise; a hairline
        // border separates them without the page appearing to vibrate.
        outlined: { borderColor: '#e3e7ec' },
      },
    },
    MuiCard: {
      defaultProps: { variant: 'outlined' },
      styleOverrides: {
        root: {
          transition: 'box-shadow 160ms ease, border-color 160ms ease',
          '&:hover': { boxShadow: '0 6px 18px rgba(20, 24, 29, 0.10)' },
        },
      },
    },
    MuiButton: {
      defaultProps: { disableElevation: true },
      styleOverrides: { root: { borderRadius: 999, paddingInline: 18 } },
    },
    MuiChip: { styleOverrides: { root: { fontWeight: 500 } } },
    MuiTextField: { defaultProps: { size: 'small' } },
    MuiAppBar: { defaultProps: { elevation: 0 } },
    MuiTooltip: { defaultProps: { arrow: true } },
  },
});

export default theme;
