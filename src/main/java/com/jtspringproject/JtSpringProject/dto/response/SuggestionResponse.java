package com.jtspringproject.JtSpringProject.dto.response;

/**
 * One search-as-you-type suggestion.
 *
 * @param text         the completed query, lower case the way marketplaces show it
 * @param categoryId   set when the suggestion is a department, so selecting it
 *                     browses that department instead of running a text search
 * @param categoryName display name of that department
 */
public record SuggestionResponse(String text, Integer categoryId, String categoryName) {
}
