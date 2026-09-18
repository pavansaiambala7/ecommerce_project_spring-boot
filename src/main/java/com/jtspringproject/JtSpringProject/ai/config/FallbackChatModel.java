package com.jtspringproject.JtSpringProject.ai.config;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;

/**
 * Tries each configured Gemini model in order until one answers.
 *
 * <p>The assistant stopped working because the one model it named,
 * gemini-2.0-flash, was retired: every chat request then failed with a 404 until
 * someone changed the configuration and redeployed. Models get retired, and they
 * also return 503 under heavy demand. With a fallback, neither takes the
 * assistant down.
 *
 * <p>A model that fails is skipped for a while rather than retried on every
 * message - otherwise each reply would first wait for a model known to be gone.
 * A retirement (404) is skipped for much longer than an overload (429/503).
 */
public class FallbackChatModel implements ChatLanguageModel {

	private static final Logger log = LoggerFactory.getLogger(FallbackChatModel.class);

	static final Duration RETIRED_COOLDOWN = Duration.ofMinutes(30);
	static final Duration OVERLOADED_COOLDOWN = Duration.ofMinutes(1);

	public record Candidate(String name, ChatLanguageModel model) {
	}

	private final List<Candidate> candidates;
	private final Map<String, Long> skipUntil = new ConcurrentHashMap<>();

	public FallbackChatModel(List<Candidate> candidates) {
		if (candidates.isEmpty()) {
			throw new IllegalArgumentException("At least one chat model is required");
		}
		this.candidates = List.copyOf(candidates);
	}

	@Override
	public Response<AiMessage> generate(List<ChatMessage> messages) {
		RuntimeException lastFailure = null;
		long now = System.currentTimeMillis();

		for (int i = 0; i < candidates.size(); i++) {
			Candidate candidate = candidates.get(i);
			boolean lastResort = i == candidates.size() - 1;
			// Every model cooling down still leaves the last one to try: an
			// answer from a model that recently failed beats no answer at all.
			if (!lastResort && skipUntil.getOrDefault(candidate.name(), 0L) > now) {
				continue;
			}

			try {
				Response<AiMessage> response = candidate.model().generate(messages);
				String text = response.content() == null ? null : response.content().text();
				if (text == null || text.isBlank()) {
					// Thinking models can spend their whole token budget before
					// writing anything. An empty reply is a failure, not an answer.
					throw new IllegalStateException("Model returned an empty reply (finish reason "
							+ response.finishReason() + ")");
				}
				skipUntil.remove(candidate.name());
				return response;
			} catch (RuntimeException e) {
				lastFailure = e;
				Duration cooldown = cooldownFor(e);
				if (cooldown != null) {
					skipUntil.put(candidate.name(), System.currentTimeMillis() + cooldown.toMillis());
				}
				log.warn("Chat model {} failed{}: {}", candidate.name(),
						lastResort ? "" : ", trying the next one", e.getMessage());
			}
		}
		throw lastFailure != null ? lastFailure : new IllegalStateException("No chat model was tried");
	}

	static Duration cooldownFor(Throwable e) {
		String message = String.valueOf(e.getMessage()).toLowerCase(Locale.ROOT);
		if (message.contains("404") || message.contains("not_found") || message.contains("no longer available")) {
			return RETIRED_COOLDOWN;
		}
		if (message.contains("429") || message.contains("503") || message.contains("resource_exhausted")
				|| message.contains("unavailable") || message.contains("high demand")) {
			return OVERLOADED_COOLDOWN;
		}
		return null;
	}

	public List<String> modelNames() {
		return candidates.stream().map(Candidate::name).toList();
	}
}
