import { Fragment, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth } from '../context/AuthContext';
import { formatPrice } from '../utils/format';
import ProductImage from './ProductImage';

// One session id per browser tab keeps the assistant's memory coherent across
// messages. The server namespaces it per user internally.
const SESSION_ID = `web-${Math.random().toString(36).slice(2, 10)}`;

const STARTERS = ['Phones under ₹15,000', "Today's best deals", 'Where is my order?'];

/** **bold** inside a line, rendered as elements - never as injected HTML. */
function inline(text) {
  return text.split(/(\*\*[^*]+\*\*)/g).map((part, i) =>
    part.startsWith('**') && part.endsWith('**') ? <strong key={i}>{part.slice(2, -2)}</strong> : part,
  );
}

/**
 * The model answers in light markdown: paragraphs, "- " or "* " bullets, and
 * **bold**. Shown raw, that reads as stray asterisks.
 */
function FormattedReply({ text }) {
  const blocks = [];
  let list = null;
  text.split('\n').forEach((raw) => {
    const line = raw.trimEnd();
    const bullet = line.match(/^\s*(?:[-*•]|\d+\.)\s+(.*)$/);
    if (bullet) {
      if (!list) {
        list = [];
        blocks.push({ type: 'list', items: list });
      }
      list.push(bullet[1]);
    } else {
      list = null;
      if (line.trim()) blocks.push({ type: 'p', text: line.replace(/^#+\s*/, '') });
    }
  });

  return blocks.map((block, i) =>
    block.type === 'list' ? (
      <ul key={i}>
        {block.items.map((item, j) => (
          <li key={j}>{inline(item)}</li>
        ))}
      </ul>
    ) : (
      <p key={i}>{inline(block.text)}</p>
    ),
  );
}

export default function ChatWidget() {
  const { isAuthenticated } = useAuth();
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState([]);
  const [draft, setDraft] = useState('');
  const [sending, setSending] = useState(false);
  const [actions, setActions] = useState([]);
  const logRef = useRef(null);

  useEffect(() => {
    if (logRef.current) logRef.current.scrollTop = logRef.current.scrollHeight;
  }, [messages, sending]);

  async function ask(text) {
    const message = text.trim();
    if (!message || sending) return;

    setMessages((prev) => [...prev, { from: 'user', text: message }]);
    setDraft('');
    setActions([]);
    setSending(true);

    try {
      const reply = await api.post('/api/chat', { sessionId: SESSION_ID, message });
      setMessages((prev) => [...prev, { from: 'bot', text: reply.reply, products: reply.products ?? [] }]);
      setActions(reply.suggestedActions ?? []);
    } catch (error) {
      // The chat tier allows 10 messages a minute; surface that rather than
      // failing silently.
      setMessages((prev) => [...prev, { from: 'bot', text: error.message, failed: true }]);
    } finally {
      setSending(false);
    }
  }

  if (!open) {
    return (
      <button type="button" className="chat-toggle" onClick={() => setOpen(true)}>
        <span aria-hidden="true">💬</span> Need help?
      </button>
    );
  }

  return (
    <section className="chat-panel" aria-label="Shopping assistant">
      <header className="chat-head">
        <span>
          ShopKart Assistant
          <small>Answers from our catalogue and your orders</small>
        </span>
        <button type="button" onClick={() => setOpen(false)} aria-label="Close chat">
          ×
        </button>
      </header>

      {!isAuthenticated ? (
        <div className="chat-signin">
          <p>Sign in to ask about products, deals and your orders.</p>
          <Link to="/login" className="btn" onClick={() => setOpen(false)}>
            Sign in
          </Link>
        </div>
      ) : (
        <>
          <div className="chat-log" ref={logRef}>
            {messages.length === 0 && (
              <div className="bubble bubble-bot">
                <p>Hi! I can help you find products, compare prices and check your orders.</p>
              </div>
            )}
            {messages.map((message, index) => (
              <Fragment key={index}>
                <div
                  className={`bubble ${message.from === 'user' ? 'bubble-user' : 'bubble-bot'} ${
                    message.failed ? 'bubble-failed' : ''
                  }`}
                >
                  {message.from === 'bot' ? <FormattedReply text={message.text} /> : message.text}
                </div>
                {message.products?.length > 0 && (
                  <div className="chat-products">
                    {message.products.map((product) => (
                      <Link key={product.id} to={`/product/${product.id}`} className="chat-product">
                        <ProductImage src={product.image} alt={product.name} />
                        <span className="chat-product-name">{product.name}</span>
                        <strong>{formatPrice(product.price)}</strong>
                      </Link>
                    ))}
                  </div>
                )}
              </Fragment>
            ))}
            {sending && (
              <div className="bubble bubble-bot typing" aria-label="Assistant is typing">
                <span />
                <span />
                <span />
              </div>
            )}
          </div>

          {(messages.length === 0 ? STARTERS : actions).length > 0 && (
            <div className="chat-actions">
              {(messages.length === 0 ? STARTERS : actions).map((action) => (
                <button key={action} type="button" onClick={() => ask(action)} disabled={sending}>
                  {action}
                </button>
              ))}
            </div>
          )}

          <form
            className="chat-form"
            onSubmit={(event) => {
              event.preventDefault();
              ask(draft);
            }}
          >
            <input
              value={draft}
              maxLength={2000}
              placeholder="Ask about a product or an order"
              onChange={(event) => setDraft(event.target.value)}
            />
            <button type="submit" disabled={sending || !draft.trim()}>
              Send
            </button>
          </form>
        </>
      )}
    </section>
  );
}
