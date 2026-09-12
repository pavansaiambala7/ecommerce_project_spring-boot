package com.jtspringproject.JtSpringProject.dto.response;

import com.jtspringproject.JtSpringProject.ai.service.RagProductSearchService;

public class SearchResultResponse {

    private ProductResponse product;
    private double relevanceScore;

    public SearchResultResponse() {
    }

    public SearchResultResponse(ProductResponse product, double relevanceScore) {
        this.product = product;
        this.relevanceScore = relevanceScore;
    }

    public static SearchResultResponse from(RagProductSearchService.SearchResult result) {
        return new SearchResultResponse(ProductResponse.from(result.getProduct()), result.getScore());
    }

    public ProductResponse getProduct() {
        return product;
    }

    public void setProduct(ProductResponse product) {
        this.product = product;
    }

    public double getRelevanceScore() {
        return relevanceScore;
    }

    public void setRelevanceScore(double relevanceScore) {
        this.relevanceScore = relevanceScore;
    }
}
