package com.jtspringproject.JtSpringProject.ai.service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

/**
 * Embeds the catalogue in the background, one Gemini batch at a time.
 *
 * <p>Three things a fifty-thousand product run has to survive, and how:
 *
 * <ul>
 * <li><b>Quota limits.</b> A 429 or RESOURCE_EXHAUSTED waits and retries the
 * same batch rather than skipping products or giving up. Gemini's free tier
 * allows 100 embeddings a minute and counts each product in a batch as one, so
 * a full catalogue is hours of waiting - which is why this runs in the
 * background and survives being stopped.</li>
 * <li><b>A poison batch.</b> Any other error is retried a few times, then that
 * batch is skipped and counted, so one bad row cannot stall the other
 * forty-nine thousand.</li>
 * <li><b>Restarts.</b> Progress is the vectors in the database. Starting again
 * after a deploy simply continues with whatever is still missing.</li>
 * </ul>
 *
 * <p>Only one job runs at a time; starting while one is running returns its
 * status instead of launching a second copy that would bill every call twice.
 */
@Service
public class EmbeddingJobService {

	private static final Logger log = LoggerFactory.getLogger(EmbeddingJobService.class);

	private static final int BATCH_SIZE = 100;
	private static final int RETRIES_PER_BATCH = 3;
	/**
	 * Quota errors are normal on a free key, not a failure: at 100 embeddings a
	 * minute a full catalogue is hours of them, so the job waits rather than
	 * giving up until the pauses have added up to well over an hour.
	 */
	private static final int MAX_RATE_LIMIT_RETRIES = 30;
	private static final int MAX_BACKOFF_SECONDS = 300;

	/**
	 * First wait after a quota error. Gemini's free tier caps embedding at 100
	 * requests per minute and each product in a batch counts as one, so a full
	 * batch exhausts the minute and the useful wait is the rest of that minute.
	 */
	private static final int RATE_LIMIT_BACKOFF_SECONDS = 60;

	public enum State {
		IDLE, RUNNING, COMPLETED, STOPPED, FAILED
	}

	/**
	 * @param remaining products still without a vector, read fresh from the
	 *                  database on every status request
	 */
	public record Status(State state, boolean full, int embedded, int skipped, long remaining,
			Instant startedAt, Instant finishedAt, String lastError, int backoffSeconds) {
	}

	private final EmbeddingService embeddingService;
	private final long batchDelayMs;
	private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "embedding-job");
		thread.setDaemon(true);
		return thread;
	});

	private volatile State state = State.IDLE;
	private volatile boolean full;
	private volatile int embedded;
	private volatile int skipped;
	private volatile Instant startedAt;
	private volatile Instant finishedAt;
	private volatile String lastError;
	private volatile int backoffSeconds;
	private volatile boolean stopRequested;

	public EmbeddingJobService(EmbeddingService embeddingService,
			@Value("${app.embedding.batch-delay-ms:0}") long batchDelayMs) {
		this.embeddingService = embeddingService;
		this.batchDelayMs = batchDelayMs;
	}

	public synchronized Status start(boolean fullReindex) {
		if (state == State.RUNNING) {
			return status();
		}
		state = State.RUNNING;
		full = fullReindex;
		embedded = 0;
		skipped = 0;
		startedAt = Instant.now();
		finishedAt = null;
		lastError = null;
		backoffSeconds = 0;
		stopRequested = false;
		executor.submit(this::run);
		log.info("Embedding job started (full={})", fullReindex);
		return status();
	}

	/** Asks the job to stop after the batch in flight. Resuming later loses nothing. */
	public Status stop() {
		stopRequested = true;
		return status();
	}

	public Status status() {
		long remaining;
		try {
			remaining = embeddingService.countMissing();
		} catch (Exception e) {
			remaining = -1;
		}
		return new Status(state, full, embedded, skipped, remaining, startedAt, finishedAt, lastError,
				backoffSeconds);
	}

	private void run() {
		int cursor = 0;
		int failures = 0;
		try {
			while (!stopRequested) {
				List<EmbeddingService.Row> batch = embeddingService.nextBatch(cursor, !full, BATCH_SIZE);
				if (batch.isEmpty()) {
					state = State.COMPLETED;
					break;
				}
				int lastId = batch.get(batch.size() - 1).id();

				try {
					embedded += embeddingService.embedBatch(batch);
					cursor = lastId;
					failures = 0;
					backoffSeconds = 0;
					pause(batchDelayMs);
					continue;
				} catch (Exception e) {
					failures++;
					lastError = summarise(e);
					log.warn("Embedding batch after id {} failed (attempt {}): {}", cursor, failures, lastError);

					if (isRateLimit(e)) {
						if (failures > MAX_RATE_LIMIT_RETRIES) {
							fail("Gave up after repeated quota errors. Start again later to resume: " + lastError);
							break;
						}
						// Linear, not exponential: the quota refills every minute,
						// so doubling the wait only wastes the minutes it skips.
						backoffSeconds = Math.min(MAX_BACKOFF_SECONDS,
								RATE_LIMIT_BACKOFF_SECONDS * Math.min(failures, 5));
						pause(backoffSeconds * 1000L);
						continue;
					}

					if (failures < RETRIES_PER_BATCH) {
						pause(2000);
						continue;
					}

					// Nothing has worked yet, so this is not one bad batch - it
					// is a bad key, a retired model or no network. Carrying on
					// would skip the entire catalogue one batch at a time.
					if (embedded == 0) {
						fail("Embedding is not working, so the job stopped: " + lastError);
						break;
					}
					skipped += batch.size();
					cursor = lastId;
					failures = 0;
				}
			}
			if (state == State.RUNNING) {
				state = State.STOPPED;
			}
		} catch (Throwable t) {
			fail(summarise(t));
		} finally {
			backoffSeconds = 0;
			finishedAt = Instant.now();
			log.info("Embedding job finished: state={} embedded={} skipped={}", state, embedded, skipped);
		}
	}

	private void fail(String message) {
		lastError = message;
		state = State.FAILED;
	}

	/** Sleeps in short steps so a stop request is honoured during a long backoff. */
	private void pause(long millis) {
		long until = System.currentTimeMillis() + millis;
		while (!stopRequested && System.currentTimeMillis() < until) {
			try {
				TimeUnit.MILLISECONDS.sleep(Math.min(500, until - System.currentTimeMillis()));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	static boolean isRateLimit(Throwable e) {
		for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
			String message = String.valueOf(t.getMessage()).toLowerCase(Locale.ROOT);
			if (message.contains("429") || message.contains("resource_exhausted") || message.contains("quota")
					|| message.contains("rate limit")) {
				return true;
			}
		}
		return false;
	}

	private static String summarise(Throwable e) {
		String message = e.getClass().getSimpleName() + ": " + e.getMessage();
		return message.length() > 300 ? message.substring(0, 300) : message;
	}

	@PreDestroy
	void shutdown() {
		stopRequested = true;
		executor.shutdownNow();
	}
}
