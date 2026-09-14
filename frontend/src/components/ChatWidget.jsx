import { useEffect, useRef, useState } from 'react';
import { api } from '../api/client';
import { useAuth } from '../context/AuthContext';

// One session id per browser tab keeps the assistant's memory coherent across
// messages. The server namespaces it per user internally.
const SESSION_ID = `web-${Math.random().toString(36).slice(2, 10)}`;

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

  // /api/chat requires authentication, so there is nothing to show to a signed
  // out visitor.
  if (!isAuthenticated) return null;

  async function ask(text) {
    const message = text.trim();
    if (!message || sending) return;

    setMessages((prev) => [...prev, { from: 'user', text: message }]);
    setDraft('');
    setActions([]);
    setSending(true);

    try {
      const reply = await api.post('/api/chat', { sessionId: SESSION_ID, message });
      setMessages((prev) => [...prev, { from: 'bot', text: reply.reply }]);
      setActions(reply.suggestedActions ?? []);
    } catch (error) {
      // The chat tier allows 10 messages a minute; surface that rather than
      // failing silently.
      setMessages((prev) => [...prev, { from: 'bot', text: error.message }]);
    } finally {
      setSending(false);
    }
  }

  if (!open) {
    return (
      <button type="button" className="chat-toggle" onClick={() => setOpen(true)}>
        Need help?
      </button>
    );
  }

  return (
    <section className="chat-panel">
      <header className="chat-head">
        <span>ShopKart Assistant</span>
        <button type="button" onClick={() => setOpen(false)} aria-label="Close chat">
          ×
        </button>
      </header>

      <div className="chat-log" ref={logRef}>
        {messages.length === 0 && (
          <div className="bubble bubble-bot">
            Hi! Ask me about products, your orders, or payments.
          </div>
        )}
        {messages.map((message, index) => (
          <div
            key={index}
            className={`bubble ${message.from === 'user' ? 'bubble-user' : 'bubble-bot'}`}
          >
            {message.text}
          </div>
        ))}
        {sending && <div className="bubble bubble-bot">Thinking…</div>}
      </div>

      {actions.length > 0 && (
        <div className="chat-actions">
          {actions.map((action) => (
            <button key={action} type="button" onClick={() => ask(action)}>
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
          placeholder="Type a message"
          onChange={(event) => setDraft(event.target.value)}
        />
        <button type="submit" disabled={sending}>
          Send
        </button>
      </form>
    </section>
  );
}
