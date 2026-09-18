import { useEffect, useRef, useState } from 'react';
import { Link, Navigate, useLocation } from 'react-router-dom';
import Price from '../components/Price';
import ProductImage from '../components/ProductImage';
import { browseLink, useCategoryTree, useStorefrontHome } from '../hooks/useCatalog';
import { discountOf } from '../utils/format';

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

  return (
    <section
      className={`hero hero-${slide.theme}`}
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      aria-roledescription="carousel"
    >
      <Link to={slide.link} className="hero-slide">
        <div className="hero-copy">
          <span className="hero-eyebrow">{slide.eyebrow}</span>
          <h1>{slide.title}</h1>
          {slide.text && <p>{slide.text}</p>}
          <span className="hero-cta">Shop now</span>
        </div>
        <div className="hero-images" aria-hidden="true">
          {slide.images.map((src, i) => (
            <ProductImage key={`${slide.key}-${i}`} src={src} alt="" loading="eager" />
          ))}
        </div>
      </Link>
      {slides.length > 1 && (
        <>
          <button
            type="button"
            className="hero-arrow hero-prev"
            aria-label="Previous banner"
            onClick={() => setIndex((i) => (i - 1 + slides.length) % slides.length)}
          >
            ‹
          </button>
          <button
            type="button"
            className="hero-arrow hero-next"
            aria-label="Next banner"
            onClick={() => setIndex((i) => (i + 1) % slides.length)}
          >
            ›
          </button>
          <div className="hero-dots">
            {slides.map((s, i) => (
              <button
                key={s.key}
                type="button"
                aria-label={`Banner ${i + 1}`}
                data-active={i === index % slides.length}
                onClick={() => setIndex(i)}
              />
            ))}
          </div>
        </>
      )}
    </section>
  );
}

function DealCard({ card }) {
  return (
    <article className="deal-card">
      <h2>{card.headline}</h2>
      {card.subtitle && <p className="deal-card-sub">{card.subtitle}</p>}
      <div className="deal-card-grid">
        {card.products.map((product) => (
          <Link key={product.id} to={`/product/${product.id}`} className="deal-tile">
            <span className="deal-tile-image">
              <ProductImage src={product.image} alt={product.name} />
            </span>
            <span className="deal-tile-name">{product.name}</span>
            {discountOf(product) > 0 && <span className="deal-tag">{discountOf(product)}% off</span>}
          </Link>
        ))}
      </div>
      <Link to={browseLink(card.query)} className="see-more">
        See all offers
      </Link>
    </article>
  );
}

function DealsRow({ deals }) {
  const rowRef = useRef(null);
  const scroll = (direction) =>
    rowRef.current?.scrollBy({ left: direction * rowRef.current.clientWidth * 0.8, behavior: 'smooth' });

  if (deals.length === 0) return null;
  return (
    <section className="shelf">
      <div className="shelf-head">
        <h2>Today&apos;s Deals</h2>
        <Link to={browseLink({ minDiscount: 10, sort: 'discount' })} className="see-more">
          See all deals
        </Link>
      </div>
      <div className="shelf-wrap">
        <button type="button" className="shelf-arrow" aria-label="Scroll left" onClick={() => scroll(-1)}>
          ‹
        </button>
        <div className="shelf-row" ref={rowRef}>
          {deals.map((product) => (
            <Link key={product.id} to={`/product/${product.id}`} className="shelf-item">
              <span className="shelf-image">
                <ProductImage src={product.image} alt={product.name} />
              </span>
              <span className="deal-badge">{discountOf(product)}% off</span>
              <Price product={product} size="sm" />
              <span className="shelf-name">{product.name}</span>
            </Link>
          ))}
        </div>
        <button type="button" className="shelf-arrow" aria-label="Scroll right" onClick={() => scroll(1)}>
          ›
        </button>
      </div>
    </section>
  );
}

function DepartmentTiles({ tree }) {
  if (tree.length === 0) return null;
  const formatCount = (n) => new Intl.NumberFormat('en-IN').format(n);
  return (
    <section className="shelf">
      <div className="shelf-head">
        <h2>Shop by department</h2>
      </div>
      <div className="department-tiles">
        {tree.map((department) => (
          <Link key={department.id} to={browseLink({ categoryId: department.id })} className="department-tile">
            <strong>{department.name}</strong>
            <span>{formatCount(department.productCount)} products</span>
          </Link>
        ))}
      </div>
    </section>
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

  if (error) return <div className="page-status">Could not load the store: {error.message}</div>;
  if (!home) return <div className="page-status">Loading…</div>;

  const slides = heroSlides(home);

  return (
    <div className="landing">
      <Hero slides={slides} />
      <div className={`deal-cards ${slides.length ? 'over-hero' : ''}`}>
        {home.cards.map((card) => (
          <DealCard key={card.key} card={card} />
        ))}
      </div>
      <DealsRow deals={home.deals} />
      <DepartmentTiles tree={tree} />
    </div>
  );
}
