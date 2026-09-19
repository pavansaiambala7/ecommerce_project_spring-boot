import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import Autocomplete from '@mui/material/Autocomplete';
import Box from '@mui/material/Box';
import InputAdornment from '@mui/material/InputAdornment';
import ListItem from '@mui/material/ListItem';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import CategoryOutlinedIcon from '@mui/icons-material/CategoryOutlined';
import SearchIcon from '@mui/icons-material/Search';
import { api } from '../api/client';
import { browseLink, useCategoryTree } from '../hooks/useCatalog';

// Suggestions already fetched this session, by prefix. Backspacing over "appl"
// to "app" shows the earlier list instantly instead of asking again.
const suggestionCache = new Map();

/** Waits for a pause in typing, so "apple" is one request rather than five. */
const DEBOUNCE_MS = 150;

/** Bolds the part of a suggestion the shopper has not typed yet. */
function Suggestion({ option, typed }) {
  const prefix = typed.toLowerCase();
  const matches = prefix && option.text.startsWith(prefix);
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25, width: '100%' }}>
      {option.categoryId ? (
        <CategoryOutlinedIcon fontSize="small" sx={{ color: 'text.disabled' }} />
      ) : (
        <SearchIcon fontSize="small" sx={{ color: 'text.disabled' }} />
      )}
      <Typography variant="body2" sx={{ flex: 1, minWidth: 0 }} noWrap>
        {matches ? (
          <>
            {option.text.slice(0, prefix.length)}
            <strong>{option.text.slice(prefix.length)}</strong>
          </>
        ) : (
          <strong>{option.text}</strong>
        )}
      </Typography>
      {option.categoryName && (
        <Typography variant="caption" color="primary.main" noWrap>
          in {option.categoryName}
        </Typography>
      )}
    </Box>
  );
}

export default function SearchBox() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { tree } = useCategoryTree();

  const [term, setTerm] = useState(searchParams.get('q') ?? '');
  const [scope, setScope] = useState('');
  const [options, setOptions] = useState([]);

  // A slow answer for "ap" must not overwrite the answer for "apple".
  const requestRef = useRef(null);

  useEffect(() => {
    setTerm(searchParams.get('q') ?? '');
  }, [searchParams]);

  useEffect(() => {
    const typed = term.toLowerCase().replace(/^\s+/, '');
    if (!typed) {
      setOptions([]);
      return undefined;
    }
    if (suggestionCache.has(typed)) {
      setOptions(suggestionCache.get(typed));
      return undefined;
    }

    const controller = new AbortController();
    requestRef.current?.abort();
    requestRef.current = controller;

    const timer = setTimeout(() => {
      api
        .get(`/api/products/suggest?q=${encodeURIComponent(typed)}&limit=10`, {
          auth: false,
          signal: controller.signal,
        })
        .then((list) => {
          suggestionCache.set(typed, list ?? []);
          setOptions(list ?? []);
        })
        .catch(() => {
          /* Suggestions are a convenience; plain search still works without them. */
        });
    }, DEBOUNCE_MS);

    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [term]);

  const departments = useMemo(() => tree.filter((d) => d.featured || d.children.length > 0), [tree]);

  function runSearch(query) {
    navigate(browseLink({ q: query, categoryId: scope }));
  }

  function choose(option) {
    if (!option) return;
    if (typeof option === 'string') {
      runSearch(option);
    } else if (option.categoryId) {
      // A department suggestion browses that department rather than searching
      // for its name as text.
      setTerm('');
      navigate(browseLink({ categoryId: option.categoryId }));
    } else {
      setTerm(option.text);
      runSearch(option.text);
    }
  }

  return (
    <Paper
      component="form"
      elevation={0}
      onSubmit={(event) => {
        event.preventDefault();
        if (term.trim() || scope) runSearch(term.trim());
      }}
      sx={{
        display: 'flex',
        alignItems: 'stretch',
        flex: 1,
        minWidth: 0,
        maxWidth: 820,
        borderRadius: 2,
        overflow: 'hidden',
      }}
    >
      <TextField
        select
        value={scope}
        onChange={(event) => setScope(event.target.value)}
        variant="standard"
        aria-label="Search in department"
        // Without displayEmpty the "All" option renders as a blank box, since
        // its value is the empty string.
        SelectProps={{ disableUnderline: true, displayEmpty: true }}
        sx={{
          display: { xs: 'none', md: 'block' },
          bgcolor: 'grey.100',
          minWidth: 120,
          '& .MuiInputBase-root': { height: '100%', px: 1.5, fontSize: 13 },
        }}
      >
        <MenuItem value="">All</MenuItem>
        {departments.map((department) => (
          <MenuItem key={department.id} value={department.id}>
            {department.name}
          </MenuItem>
        ))}
      </TextField>

      <Autocomplete
        freeSolo
        fullWidth
        options={options}
        filterOptions={(x) => x}
        inputValue={term}
        onInputChange={(_event, value, reason) => reason !== 'reset' && setTerm(value)}
        onChange={(_event, value) => choose(value)}
        getOptionLabel={(option) => (typeof option === 'string' ? option : option.text)}
        isOptionEqualToValue={(a, b) => a.text === b.text && a.categoryId === b.categoryId}
        renderOption={(props, option) => (
          <ListItem {...props} key={`${option.text}-${option.categoryId ?? ''}`} dense>
            <Suggestion option={option} typed={term.replace(/^\s+/, '')} />
          </ListItem>
        )}
        renderInput={(params) => (
          <TextField
            {...params}
            placeholder="Search ShopKart"
            variant="standard"
            size="medium"
            InputProps={{
              ...params.InputProps,
              disableUnderline: true,
              sx: { px: 1.5, height: '100%' },
              endAdornment: (
                <InputAdornment position="end" sx={{ height: '100%', maxHeight: 'none', m: 0 }}>
                  <Box
                    component="button"
                    type="submit"
                    aria-label="Search"
                    sx={{
                      border: 0,
                      cursor: 'pointer',
                      px: 2.5,
                      height: 42,
                      display: 'grid',
                      placeItems: 'center',
                      bgcolor: 'secondary.main',
                      color: 'secondary.contrastText',
                      '&:hover': { bgcolor: 'secondary.dark' },
                    }}
                  >
                    <SearchIcon />
                  </Box>
                </InputAdornment>
              ),
            }}
          />
        )}
        sx={{ flex: 1, '& .MuiAutocomplete-endAdornment': { display: 'none' } }}
      />
    </Paper>
  );
}
