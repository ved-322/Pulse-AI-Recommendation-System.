package com.recsys.model;

import java.util.Map;

/**
 * A scored suggestion produced by the engine.
 *
 * @param score     blended score in 0..1
 * @param source    the signal that contributed most: content | collaborative | popularity
 * @param reason    human readable explanation ("why am I seeing this?")
 * @param breakdown weighted contribution of every signal, used for explainability in the UI
 */
public record Recommendation(Item item, double score, int matchPercent, String source,
                             String reason, Map<String, Double> breakdown) {
}
