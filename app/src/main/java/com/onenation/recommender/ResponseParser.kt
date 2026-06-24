package com.onenation.recommender
object ResponseParser {
    fun parse(response: String): RecommendationResult {
        val clean = response.lowercase().trim()
        return when {
            clean.isBlank() ||
                clean.contains("error") ||
                clean.contains("invalid") ||
                clean.contains("incomplete") ||
                clean.contains("try again") ||
                clean.contains("failed") ||
                clean.contains("problem") -> RecommendationResult.FAILED
            clean.contains("already been recommended") ||
                clean.contains("already recommended") ||
                clean.contains("has already been recommended") -> RecommendationResult.ALREADY_RECOMMENDED
            clean.contains("already has") ||
                clean.contains("not eligible") ||
                clean.contains("already installed") -> RecommendationResult.ALREADY_INSTALLED
            clean.contains("submitted") ||
                clean.contains("will earn") ||
                clean.contains("commission") -> RecommendationResult.SUBMITTED
            else -> RecommendationResult.FAILED
        }
    }
}
enum class RecommendationResult { ALREADY_RECOMMENDED, ALREADY_INSTALLED, SUBMITTED, FAILED }
