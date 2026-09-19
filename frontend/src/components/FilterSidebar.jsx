import { useEffect, useState } from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Checkbox from '@mui/material/Checkbox';
import Divider from '@mui/material/Divider';
import FormControlLabel from '@mui/material/FormControlLabel';
import Link from '@mui/material/Link';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemText from '@mui/material/ListItemText';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import { findInTree, useCategoryTree } from '../hooks/useCatalog';

const PRICE_RANGES = [
  { label: 'Under ₹1,000', min: null, max: 1000 },
  { label: '₹1,000 – ₹5,000', min: 1000, max: 5000 },
  { label: '₹5,000 – ₹10,000', min: 5000, max: 10000 },
  { label: '₹10,000 – ₹20,000', min: 10000, max: 20000 },
  { label: 'Over ₹20,000', min: 20000, max: null },
];

const DISCOUNTS = [10, 25, 50, 70];

/** A filter heading, so every block in the rail lines up. */
function Block({ title, children }) {
  return (
    <Box sx={{ py: 1.5 }}>
      <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 0.5 }}>
        {title}
      </Typography>
      {children}
      <Divider sx={{ mt: 1.5 }} />
    </Box>
  );
}

/**
 * Department, price, discount and availability filters.
 *
 * <p>Everything maps to a URL parameter rather than component state, so a
 * filtered view can be linked, bookmarked and navigated back to.
 */
export default function FilterSidebar({ params, onChange }) {
  const { tree } = useCategoryTree();
  const [minPrice, setMinPrice] = useState(params.minPrice ?? '');
  const [maxPrice, setMaxPrice] = useState(params.maxPrice ?? '');

  // Keep the inputs in step when the URL changes from elsewhere, such as the
  // back button or a department link in the top bar.
  useEffect(() => {
    setMinPrice(params.minPrice ?? '');
    setMaxPrice(params.maxPrice ?? '');
  }, [params.minPrice, params.maxPrice]);

  function applyPrice(event) {
    event.preventDefault();
    onChange({ minPrice: minPrice || null, maxPrice: maxPrice || null, page: 0 });
  }

  const { node, parent } = findInTree(tree, params.categoryId);
  // Inside a department, show where you are and what is below it rather than
  // the full list of thirty departments.
  const top = parent ?? node;

  const isRange = (range) =>
    String(params.minPrice ?? '') === String(range.min ?? '') &&
    String(params.maxPrice ?? '') === String(range.max ?? '');

  const itemSx = (selected) => ({
    borderRadius: 1,
    py: 0.25,
    '& .MuiListItemText-primary': {
      fontSize: 14,
      fontWeight: selected ? 700 : 400,
      color: selected ? 'primary.main' : 'text.primary',
    },
  });

  return (
    <Box component="aside">
      <Block title="Department">
        <List dense disablePadding>
          {top ? (
            <>
              <Link
                component="button"
                type="button"
                underline="hover"
                onClick={() => onChange({ categoryId: null, page: 0 })}
                sx={{ display: 'inline-flex', alignItems: 'center', fontSize: 14, mb: 0.5 }}
              >
                <ChevronLeftIcon fontSize="small" /> Any Department
              </Link>
              <ListItemButton
                onClick={() => onChange({ categoryId: top.id, page: 0 })}
                sx={itemSx(node === top)}
              >
                <ListItemText primary={top.name} />
              </ListItemButton>
              {top.children.map((child) => (
                <ListItemButton
                  key={child.id}
                  onClick={() => onChange({ categoryId: child.id, page: 0 })}
                  sx={{ ...itemSx(node?.id === child.id), pl: 3 }}
                >
                  <ListItemText primary={child.name} />
                </ListItemButton>
              ))}
            </>
          ) : (
            tree.map((department) => (
              <ListItemButton
                key={department.id}
                onClick={() => onChange({ categoryId: department.id, page: 0 })}
                sx={itemSx(false)}
              >
                <ListItemText primary={department.name} />
              </ListItemButton>
            ))
          )}
        </List>
      </Block>

      <Block title="Price">
        <List dense disablePadding>
          {PRICE_RANGES.map((range) => (
            <ListItemButton
              key={range.label}
              onClick={() => onChange({ minPrice: range.min, maxPrice: range.max, page: 0 })}
              sx={itemSx(isRange(range))}
            >
              <ListItemText primary={range.label} />
            </ListItemButton>
          ))}
        </List>
        <Box
          component="form"
          onSubmit={applyPrice}
          sx={{
            display: 'flex',
            gap: 0.75,
            mt: 1,
            // The spinner arrows cost about 20px a field in a rail this
            // narrow, which is enough to clip the placeholder.
            '& input[type=number]': { MozAppearance: 'textfield' },
            '& input::-webkit-outer-spin-button, & input::-webkit-inner-spin-button': {
              WebkitAppearance: 'none',
              margin: 0,
            },
          }}
        >
          <TextField
            type="number"
            placeholder="Min"
            value={minPrice}
            onChange={(event) => setMinPrice(event.target.value)}
            inputProps={{ min: 0, 'aria-label': 'Minimum price' }}
            sx={{ flex: 1, minWidth: 0 }}
          />
          <TextField
            type="number"
            placeholder="Max"
            value={maxPrice}
            onChange={(event) => setMaxPrice(event.target.value)}
            inputProps={{ min: 0, 'aria-label': 'Maximum price' }}
            sx={{ flex: 1, minWidth: 0 }}
          />
          <Button type="submit" variant="outlined" size="small" sx={{ flexShrink: 0, px: 1.5 }}>
            Go
          </Button>
        </Box>
      </Block>

      <Block title="Discount">
        <List dense disablePadding>
          {DISCOUNTS.map((discount) => (
            <ListItemButton
              key={discount}
              onClick={() =>
                onChange({
                  minDiscount: String(params.minDiscount) === String(discount) ? null : discount,
                  page: 0,
                })
              }
              sx={itemSx(String(params.minDiscount) === String(discount))}
            >
              <ListItemText primary={`${discount}% off or more`} />
            </ListItemButton>
          ))}
        </List>
      </Block>

      <Block title="Availability">
        <FormControlLabel
          control={
            <Checkbox
              size="small"
              checked={Boolean(params.inStockOnly)}
              onChange={(event) => onChange({ inStockOnly: event.target.checked || null, page: 0 })}
            />
          }
          label={<Typography variant="body2">In stock only</Typography>}
        />
      </Block>
    </Box>
  );
}
