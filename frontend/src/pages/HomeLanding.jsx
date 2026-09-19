import { useEffect, useRef, useState } from 'react';
import { Link as RouterLink, Navigate, useLocation } from 'react-router-dom';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import Container from '@mui/material/Container';
import IconButton from '@mui/material/IconButton';
import Link from '@mui/material/Link';
import Paper from '@mui/material/Paper';
import Skeleton from '@mui/material/Skeleton';
import Typography from '@mui/material/Typography';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import Price from '../components/Price';
import ProductImage from '../components/ProductImage';
import { browseLink, useCategoryTree, useStorefrontHome } from '../hooks/useCatalog';
import { discountOf } from '../utils/format';

/** Banner backgrounds. Material has no opinion about brand artwork, so these live here. */
const THEMES = {
  sunrise: 'linear-gradient(120deg, #ff8a4c 0%, #ffb300 100%)',
  rose: 'linear-gradient(120deg, #d81b60 0%, #ff8a65 100%)',
  ocean: 'linear-gradient(120deg, #01579b 0%, #00acc1 100%)',
  dusk: 'linear-gradient(120deg, #4527a0 0%, #7e57c2 100%)',
  leaf: 'linear-gradient(120deg, #1b5e20 0%, #66bb6a 100%)',
};

/** A banner looks best with photographs, so products that have one go first. */
function withPhotosFirst(products) {
  const hasPhoto = (p) => p.image && !p.image.includes('placehold.co');
  return [...products.filter(hasPhoto), ...products.filter((p) => !hasPhoto(p))];
}

/** Hero banners are built from the catalogue's real cards, never from invented offers. */
function heroSlides(home) {
  const byKey = Object.fromEntries(home.cards.map((card) => [card.key, card]));
  const slides = [];
  const bestDeal = home.deals.reduce((best, p) => Math.max(best, discountOf(p)), 0);

  if (home.deals.length >= 3) {
    slides.push({
      key: 'deals',
      theme: 'sunrise',
      eyebrow: "Today's Deals",
      title: `Up to ${bestDeal}% off`,
      text: 'The biggest savings across every department, in stock now.',
      link: browseLink({ minDiscount: 10, sort: 'discount' }),
      images: withPhotosFirst(home.deals).slice(0, 3).map((p) => p.image),
    });
  }
  const fashion = [byKey.men, byKey.women].filter(Boolean);
  if (fashion.length) {
    slides.push({
      key: 'fashion',
      theme: 'rose',
      eyebrow: 'Fashion',
      title: 'Styles for men and women',
      text: fashion.map((card) => card.headline).join(' · '),
      link: browseLink(fashion[0].query),
      images: withPhotosFirst(fashion.flatMap((card) => card.products)).map((p) => p.image).slice(0, 3),
    });
  }
  for (const key of ['mobiles', 'electronics', 'grocery']) {
    const card = byKey[key];
    if (card && slides.length < 4) {
      slides.push({
        key,
        theme: key === 'mobiles' ? 'ocean' : key === 'grocery' ? 'leaf' : 'dusk',
        eyebrow: card.subtitle ?? 'Featured',
        title: card.headline,
        text: card.products.map((p) => p.brand).filter(Boolean).slice(0, 3).join(', '),
        link: browseLink(card.query),
        images: withPhotosFirst(card.products).slice(0, 3).map((p) => p.image),
      });
    }
  }
  return slides;
}

function Hero({ slides }) {
  const [index, setIndex] = useState(0);
  const [paused, setPaused] = useState(false);

  useEffect(() => {
    if (paused || slides.length < 2) return undefined;
    const timer = setInterval(() => setIndex((i) => (i + 1) % slides.length), 6000);
    return () => clearInterval(timer);
  }, [paused, slides.length]);

  if (slides.length === 0) return null;
  const slide = slides[index % slides.length];

  const arrowSx = {
    // Hidden on a phone, where the banner is only as wide as the copy and an
    // arrow would sit on top of the words.
    display: { xs: 'none', md: 'inline-flex' },
    position: 'absolute',
    top: '50%',
    transform: 'translateY(-50%)',
    bgcolor: 'rgba(255,255,255,0.85)',
    '&:hover': { bgcolor: 'common.white' },
  };

  return (
    <Box
      component="section"
      aria-roledescription="carousel"
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      sx={{ position: 'relative', background: THEMES[slide.theme], color: 'common.white', transition: 'background 400ms' }}
    >
      <Container maxWidth="xl">
        <Box
          component={RouterLink}
          to={slide.link}
          sx={{
            display: 'grid',
            gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' },
            alignItems: 'center',
            gap: 3,
            py: { xs: 4, md: 6 },
            px: { xs: 1, md: 4 },
            color: 'inherit',
            textDecoration: 'none',
            minHeight: { xs: 280, md: 330 },
          }}
        >
          <Box>
            <Typography variant="overline" sx={{ opacity: 0.9, letterSpacing: 1.5 }}>
              {slide.eyebrow}
            </Typography>
            <Typography variant="h1" sx={{ fontWeight: 700, my: 1 }}>
              {slide.title}
            </Typography>
            {slide.text && (
              <Typography variant="body1" sx={{ opacity: 0.92, mb: 2.5, maxWidth: 460 }}>
                {slide.text}
              </Typography>
            )}
            <Button variant="contained" color="secondary" size="large">
              Shop now
            </Button>
          </Box>

          <Box
            aria-hidden="true"
            sx={{ display: { xs: 'none', md: 'grid' }, gridTemplateColumns: 'repeat(3, 1fr)', gap: 2 }}
          >
            {slide.images.map((src, i) => (
              <Paper
                key={`${slide.key}-${i}`}
                elevation={6}
                sx={{ height: 170, p: 1.5, bgcolor: 'common.white', borderRadius: 2 }}
              >
                <ProductImage src={src} alt="" loading="eager" />
              </Paper>
            ))}
          </Box>
        </Box>
      </Container>

      {slides.length > 1 && (
        <>
          <IconButton
            aria-label="Previous banner"
            onClick={() => setIndex((i) => (i - 1 + slides.length) % slides.length)}
            sx={{ ...arrowSx, left: { xs: 4, md: 16 } }}
          >
            <ChevronLeftIcon />
          </IconButton>
          <IconButton
            aria-label="Next banner"
            onClick={() => setIndex((i) => (i + 1) % slides.length)}
            sx={{ ...arrowSx, right: { xs: 4, md: 16 } }}
          >
            <ChevronRightIcon />
          </IconButton>
          <Box sx={{ display: 'flex', justifyContent: 'center', gap: 1, pb: 1.5 }}>
            {slides.map((s, i) => (
              <Box
                key={s.key}
                component="button"
                type="button"
                aria-label={`Banner ${i + 1}`}
                onClick={() => setIndex(i)}
                sx={{
                  width: i === index % slides.length ? 26 : 9,
                  height: 9,
                  p: 0,
                  border: 0,
                  borderRadius: 999,
                  cursor: 'pointer',
                  transition: 'width 200ms',
                  bgcolor: i === index % slides.length ? 'common.white' : 'rgba(255,255,255,0.5)',
                }}
              />
            ))}
          </Box>
        </>
      )}
    </Box>
  );
}

function DealCard({ card }) {
  return (
    <Card sx={{ display: 'flex', flexDirection: 'column' }}>
      <CardContent sx={{ flex: 1 }}>
        <Typography variant="h3" gutterBottom>
          {card.headline}
        </Typography>
        {card.subtitle && (
          <Typography variant="body2" color="text.secondary" gutterBottom>
            {card.subtitle}
          </Typography>
        )}
        <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 1.5, mt: 1.5 }}>
          {card.products.map((product) => (
            <Box
              key={product.id}
              component={RouterLink}
              to={`/product/${product.id}`}
              sx={{ textDecoration: 'none', color: 'inherit', position: 'relative' }}
            >
              <Box sx={{ height: 104, bgcolor: 'common.white', borderRadius: 1, p: 0.5 }}>
                <ProductImage src={product.image} alt={product.name} />
              </Box>
              <Typography variant="caption" className="clamp-2" sx={{ display: 'block', mt: 0.5 }}>
                {product.name}
              </Typography>
              {discountOf(product) > 0 && (
                <Chip
                  label={`${discountOf(product)}% off`}
                  size="small"
                  color="error"
                  sx={{ position: 'absolute', top: 4, left: 4, height: 20, fontSize: 11 }}
                />
              )}
            </Box>
          ))}
        </Box>
      </CardContent>
      <Box sx={{ px: 2, pb: 2 }}>
        <Link component={RouterLink} to={browseLink(card.query)} underline="hover" variant="body2">
          See all offers
        </Link>
      </Box>
    </Card>
  );
}

function Shelf({ title, action, children }) {
  const rowRef = useRef(null);
  const scroll = (direction) =>
    rowRef.current?.scrollBy({ left: direction * rowRef.current.clientWidth * 0.8, behavior: 'smooth' });

  const arrowSx = {
    position: 'absolute',
    top: '50%',
    transform: 'translateY(-50%)',
    zIndex: 1,
    bgcolor: 'background.paper',
    boxShadow: 2,
    '&:hover': { bgcolor: 'background.paper' },
  };

  return (
    <Paper sx={{ p: 2, mt: 3 }}>
      <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 2, mb: 1.5 }}>
        <Typography variant="h2">{title}</Typography>
        {action}
      </Box>
      <Box sx={{ position: 'relative' }}>
        <IconButton aria-label="Scroll left" onClick={() => scroll(-1)} sx={{ ...arrowSx, left: -8 }}>
          <ChevronLeftIcon />
        </IconButton>
        <Box
          ref={rowRef}
          className="no-scrollbar"
          sx={{ display: 'flex', gap: 2, overflowX: 'auto', scrollSnapType: 'x mandatory', py: 0.5 }}
        >
          {children}
        </Box>
        <IconButton aria-label="Scroll right" onClick={() => scroll(1)} sx={{ ...arrowSx, right: -8 }}>
          <ChevronRightIcon />
        </IconButton>
      </Box>
    </Paper>
  );
}

function DealsRow({ deals }) {
  if (deals.length === 0) return null;
  return (
    <Shelf
      title="Today's Deals"
      action={
        <Link
          component={RouterLink}
          to={browseLink({ minDiscount: 10, sort: 'discount' })}
          underline="hover"
          variant="body2"
        >
          See all deals
        </Link>
      }
    >
      {deals.map((product) => (
        <Box
          key={product.id}
          component={RouterLink}
          to={`/product/${product.id}`}
          sx={{
            width: 168,
            flexShrink: 0,
            scrollSnapAlign: 'start',
            textDecoration: 'none',
            color: 'inherit',
          }}
        >
          <Box sx={{ height: 150, bgcolor: 'common.white', borderRadius: 1, p: 1 }}>
            <ProductImage src={product.image} alt={product.name} />
          </Box>
          <Chip label={`${discountOf(product)}% off`} size="small" color="error" sx={{ my: 0.75 }} />
          <Price product={product} size="sm" />
          <Typography variant="caption" className="clamp-2" color="text.secondary" sx={{ display: 'block' }}>
            {product.name}
          </Typography>
        </Box>
      ))}
    </Shelf>
  );
}

function DepartmentTiles({ tree }) {
  if (tree.length === 0) return null;
  const formatCount = (n) => new Intl.NumberFormat('en-IN').format(n);
  return (
    <Paper sx={{ p: 2, mt: 3 }}>
      <Typography variant="h2" sx={{ mb: 1.5 }}>
        Shop by department
      </Typography>
      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: { xs: 'repeat(2, 1fr)', sm: 'repeat(3, 1fr)', md: 'repeat(6, 1fr)' },
          gap: 1.5,
        }}
      >
        {tree.map((department) => (
          <Card
            key={department.id}
            component={RouterLink}
            to={browseLink({ categoryId: department.id })}
            sx={{ p: 1.75, textDecoration: 'none', color: 'inherit' }}
          >
            <Typography variant="body2" sx={{ fontWeight: 600 }} className="clamp-2">
              {department.name}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {formatCount(department.productCount)} products
            </Typography>
          </Card>
        ))}
      </Box>
    </Paper>
  );
}

export default function HomeLanding() {
  const { search } = useLocation();
  const { home, error } = useStorefrontHome();
  const { tree } = useCategoryTree();

  // Search and department links used to point at "/?q=..."; send them to the
  // results page they now belong to.
  const params = new URLSearchParams(search);
  if (['q', 'categoryId', 'sort', 'minPrice', 'maxPrice'].some((key) => params.has(key))) {
    return <Navigate to={`/s${search}`} replace />;
  }

  if (error) {
    return (
      <Container maxWidth="xl" sx={{ py: 4 }}>
        <Alert severity="error">Could not load the store: {error.message}</Alert>
      </Container>
    );
  }

  if (!home) {
    return (
      <Box>
        <Skeleton variant="rectangular" height={330} />
        <Container maxWidth="xl" sx={{ py: 3 }}>
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(4, 1fr)' }, gap: 2 }}>
            {[0, 1, 2, 3].map((i) => (
              <Skeleton key={i} variant="rounded" height={300} />
            ))}
          </Box>
        </Container>
      </Box>
    );
  }

  const slides = heroSlides(home);

  return (
    <Box>
      <Hero slides={slides} />
      <Container maxWidth="xl" sx={{ pb: 4, mt: slides.length ? -5 : 3, position: 'relative' }}>
        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', lg: 'repeat(4, 1fr)' },
            gap: 2,
          }}
        >
          {home.cards.map((card) => (
            <DealCard key={card.key} card={card} />
          ))}
        </Box>
        <DealsRow deals={home.deals} />
        <DepartmentTiles tree={tree} />
      </Container>
    </Box>
  );
}
