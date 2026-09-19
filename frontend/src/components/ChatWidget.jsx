import { Fragment, useEffect, useRef, useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import Avatar from '@mui/material/Avatar';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Fab from '@mui/material/Fab';
import IconButton from '@mui/material/IconButton';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import Zoom from '@mui/material/Zoom';
import CloseIcon from '@mui/icons-material/Close';
import SendIcon from '@mui/icons-material/Send';
import SmartToyOutlinedIcon from '@mui/icons-material/SmartToyOutlined';
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
      <Box key={i} component="ul" sx={{ pl: 2.5, my: 0.5, fontSize: 14 }}>
        {block.items.map((item, j) => (
          <li key={j}>{inline(item)}</li>
        ))}
      </Box>
    ) : (
      <Typography key={i} variant="body2" sx={{ '& + &': { mt: 1 } }}>
        {inline(block.text)}
      </Typography>
    ),
  );
}

/** The three-dot "assistant is typing" indicator. */
function Typing() {
  return (
    <Box sx={{ display: 'flex', gap: 0.5, px: 0.5 }} aria-label="Assistant is typing">
      {[0, 1, 2].map((i) => (
        <Box
          key={i}
          sx={{
            width: 7,
            height: 7,
            borderRadius: '50%',
            bgcolor: 'text.disabled',
            animation: 'chatDot 1.2s infinite ease-in-out',
            animationDelay: `${i * 0.18}s`,
            '@keyframes chatDot': {
              '0%, 60%, 100%': { opacity: 0.25, transform: 'translateY(0)' },
              '30%': { opacity: 1, transform: 'translateY(-3px)' },
            },
          }}
        />
      ))}
    </Box>
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

  const chips = messages.length === 0 ? STARTERS : actions;

  return (
    <>
      <Zoom in={!open}>
        <Fab
          color="secondary"
          variant="extended"
          onClick={() => setOpen(true)}
          sx={{ position: 'fixed', right: 24, bottom: 24, zIndex: 1200 }}
        >
          <SmartToyOutlinedIcon sx={{ mr: 1 }} />
          Need help?
        </Fab>
      </Zoom>

      <Zoom in={open} unmountOnExit>
        <Paper
          elevation={8}
          aria-label="Shopping assistant"
          sx={{
            position: 'fixed',
            right: { xs: 12, sm: 24 },
            bottom: { xs: 12, sm: 24 },
            width: { xs: 'calc(100vw - 24px)', sm: 380 },
            height: { xs: '70vh', sm: 540 },
            display: 'flex',
            flexDirection: 'column',
            overflow: 'hidden',
            zIndex: 1200,
          }}
        >
          <Box
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 1.25,
              px: 2,
              py: 1.25,
              bgcolor: 'primary.main',
              color: 'common.white',
            }}
          >
            <Avatar sx={{ bgcolor: 'primary.dark', width: 34, height: 34 }}>
              <SmartToyOutlinedIcon fontSize="small" />
            </Avatar>
            <Box sx={{ flex: 1, lineHeight: 1.2 }}>
              <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                ShopKart Assistant
              </Typography>
              <Typography variant="caption" sx={{ opacity: 0.85 }}>
                Answers from our catalogue and your orders
              </Typography>
            </Box>
            <IconButton size="small" onClick={() => setOpen(false)} aria-label="Close chat" sx={{ color: 'inherit' }}>
              <CloseIcon fontSize="small" />
            </IconButton>
          </Box>

          {!isAuthenticated ? (
            <Stack spacing={2} sx={{ p: 3, alignItems: 'center', justifyContent: 'center', flex: 1 }}>
              <Typography variant="body2" align="center" color="text.secondary">
                Sign in to ask about products, deals and your orders.
              </Typography>
              <Button component={RouterLink} to="/login" variant="contained" onClick={() => setOpen(false)}>
                Sign in
              </Button>
            </Stack>
          ) : (
            <>
              <Box ref={logRef} sx={{ flex: 1, overflowY: 'auto', p: 1.5, bgcolor: 'background.default' }}>
                {messages.length === 0 && (
                  <Paper variant="outlined" sx={{ p: 1.25, mb: 1, maxWidth: '85%' }}>
                    <Typography variant="body2">
                      Hi! I can help you find products, compare prices and check your orders.
                    </Typography>
                  </Paper>
                )}
                {messages.map((message, index) => (
                  <Fragment key={index}>
                    <Box sx={{ display: 'flex', justifyContent: message.from === 'user' ? 'flex-end' : 'flex-start' }}>
                      <Paper
                        variant="outlined"
                        sx={{
                          p: 1.25,
                          mb: 1,
                          maxWidth: '85%',
                          borderColor: 'transparent',
                          bgcolor: message.failed
                            ? 'error.main'
                            : message.from === 'user'
                              ? 'primary.main'
                              : 'background.paper',
                          color: message.from === 'user' || message.failed ? 'common.white' : 'text.primary',
                        }}
                      >
                        {message.from === 'bot' && !message.failed ? (
                          <FormattedReply text={message.text} />
                        ) : (
                          <Typography variant="body2">{message.text}</Typography>
                        )}
                      </Paper>
                    </Box>
                    {message.products?.length > 0 && (
                      <Box className="no-scrollbar" sx={{ display: 'flex', gap: 1, overflowX: 'auto', mb: 1, pb: 0.5 }}>
                        {message.products.map((product) => (
                          <Paper
                            key={product.id}
                            component={RouterLink}
                            to={`/product/${product.id}`}
                            variant="outlined"
                            sx={{
                              width: 118,
                              flexShrink: 0,
                              p: 1,
                              textDecoration: 'none',
                              color: 'inherit',
                              bgcolor: 'background.paper',
                            }}
                          >
                            <Box sx={{ height: 70 }}>
                              <ProductImage src={product.image} alt={product.name} />
                            </Box>
                            <Typography variant="caption" className="clamp-2" sx={{ display: 'block', mt: 0.5 }}>
                              {product.name}
                            </Typography>
                            <Typography variant="caption" sx={{ fontWeight: 700 }}>
                              {formatPrice(product.price)}
                            </Typography>
                          </Paper>
                        ))}
                      </Box>
                    )}
                  </Fragment>
                ))}
                {sending && <Typing />}
              </Box>

              {chips.length > 0 && (
                <Box sx={{ display: 'flex', gap: 0.75, flexWrap: 'wrap', px: 1.5, py: 1 }}>
                  {chips.map((action) => (
                    <Chip
                      key={action}
                      label={action}
                      size="small"
                      variant="outlined"
                      clickable
                      disabled={sending}
                      onClick={() => ask(action)}
                    />
                  ))}
                </Box>
              )}

              <Box
                component="form"
                onSubmit={(event) => {
                  event.preventDefault();
                  ask(draft);
                }}
                sx={{ display: 'flex', gap: 1, p: 1.5, borderTop: 1, borderColor: 'divider' }}
              >
                <TextField
                  fullWidth
                  value={draft}
                  placeholder="Ask about a product or an order"
                  inputProps={{ maxLength: 2000 }}
                  onChange={(event) => setDraft(event.target.value)}
                />
                <IconButton type="submit" color="primary" disabled={sending || !draft.trim()} aria-label="Send">
                  <SendIcon />
                </IconButton>
              </Box>
            </>
          )}
        </Paper>
      </Zoom>
    </>
  );
}
