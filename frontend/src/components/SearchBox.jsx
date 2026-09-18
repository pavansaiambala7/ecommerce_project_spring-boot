import { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { api } from '../api/client';
import { browseLink, useCategoryTree } from '../hooks/useCatalog';

// Suggestions already fetched this session, by prefix. Backspacing over "appl"
// to "app" shows the earlier list instantly instead of asking again.
const suggestionCache = new Map();

/** Waits for a pause in typing, so "apple" is one request rather than five. */
const DEBOUNCE_MS = 150;

/** Bolds the part of a suggestion the shopper has not typed yet, as Amazon does. */
function Highlight({ text, typed }) {
  const prefix = typed.toLowerCase();
  if (prefix && text.startsWith(prefix)) {
    return (
      <>
        {text.slice(0, prefix.length)}
        <strong>{text.slice(prefix.length)}</strong>
      </>
    );
  }
  return <strong>{text}</strong>;
}

export default function SearchBox() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { tree } = useCategoryTree();

  const [term, setTerm] = useState(searchParams.get('q') ?? '');
  const [scope, setScope] = useState('');
  const [suggestions, setSuggestions] = useState([]);
  const [open, setOpen] = useState(false);
  const [highlighted, setHighlighted] = useState(-1);
  const inputRef = useRef(null);

  // Keep the box in step with the URL: after a search, or when the back
  // button returns to an earlier one.
  useEffect(() => {
    setTerm(searchParams.get('q') ?? '');
  }, [searchParams]);

  useEffect(() => {
    const typed = term.toLowerCase().replace(/^\s+/, '');
    if (!typed) {
      setSuggestions([]);
      return undefined;
    }
    if (suggestionCache.has(typed)) {
      setSuggestions(suggestionCache.get(typed));
      return undefined;
    }

    // A slow response for "ap" must not overwrite the answer for "apple".
    const controller = new AbortController();
    const timer = setTimeout(() => {
      api
        .get(`/api/products/suggest?q=${encodeURIComponent(typed)}&limit=10`, {
          auth: false,
          signal: controller.signal,
        })
        .then((list) => {
          suggestionCache.set(typed, list ?? []);
          setSuggestions(list ?? []);
          setHighlighted(-1);
        })
        .catch(() => {
          /* Suggestions are a convenience; a failure leaves plain search working. */
        });
    }, DEBOUNCE_MS);

    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [term]);

  function go(params) {
    setOpen(false);
    inputRef.current?.blur();
    navigate(browseLink(params));
  }

  function choose(suggestion) {
    if (suggestion.categoryId) {
      // A department suggestion browses the department rather than running a
      // text search for its name.
      setTerm('');
      go({ categoryId: suggestion.categoryId });
    } else {
      setTerm(suggestion.text);
      go({ q: suggestion.text, categoryId: scope });
    }
  }

  function submit(event) {
    event.preventDefault();
    if (highlighted >= 0 && suggestions[highlighted]) {
      choose(suggestions[highlighted]);
      return;
    }
    const query = term.trim();
    if (query || scope) go({ q: query, categoryId: scope });
  }

  function onKeyDown(event) {
    if (!open || suggestions.length === 0) return;
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setHighlighted((index) => (index + 1) % suggestions.length);
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setHighlighted((index) => (index <= 0 ? suggestions.length - 1 : index - 1));
    } else if (event.key === 'Escape') {
      setOpen(false);
    }
  }

  const showList = open && term.trim() !== '' && suggestions.length > 0;

  return (
    <form className="search" onSubmit={submit} role="search">
      <select
        className="search-scope"
        value={scope}
        onChange={(event) => setScope(event.target.value)}
        aria-label="Search in department"
      >
        <option value="">All</option>
        {tree.map((department) => (
          <option key={department.id} value={department.id}>
            {department.name}
          </option>
        ))}
      </select>

      <div className="search-field">
        <input
          ref={inputRef}
          type="search"
          value={term}
          placeholder="Search ShopKart"
          aria-label="Search products"
          autoComplete="off"
          aria-autocomplete="list"
          aria-expanded={showList}
          aria-controls="search-suggestions"
          onChange={(event) => {
            setTerm(event.target.value);
            setOpen(true);
          }}
          onFocus={() => setOpen(true)}
          // Delayed so a click on a suggestion lands before the list closes.
          onBlur={() => setTimeout(() => setOpen(false), 120)}
          onKeyDown={onKeyDown}
        />

        {showList && (
          <ul className="suggestions" id="search-suggestions" role="listbox">
            {suggestions.map((suggestion, index) => (
              <li
                key={`${suggestion.text}-${suggestion.categoryId ?? ''}`}
                role="option"
                aria-selected={index === highlighted}
                data-active={index === highlighted}
                onMouseDown={(event) => event.preventDefault()}
                onMouseEnter={() => setHighlighted(index)}
                onClick={() => choose(suggestion)}
              >
                <span className="suggestion-icon" aria-hidden="true">
                  {suggestion.categoryId ? '▦' : '⌕'}
                </span>
                <span>
                  <Highlight text={suggestion.text} typed={term.replace(/^\s+/, '')} />
                  {suggestion.categoryId && <em className="suggestion-scope"> in {suggestion.categoryName}</em>}
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>

      <button type="submit" aria-label="Search">
        <svg width="20" height="20" viewBox="0 0 24 24" aria-hidden="true">
          <circle cx="10.5" cy="10.5" r="6.5" fill="none" stroke="currentColor" strokeWidth="2.4" />
          <path d="M15.5 15.5 21 21" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" />
        </svg>
      </button>
    </form>
  );
}
