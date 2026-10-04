package com.mar.agent.sdk.core

import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractionParams
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import kotlinx.coroutines.tasks.await
import java.util.Locale

/**
 * SlotExtractor: Uses ML Kit Entity Extraction (Tier 0) to parse parameters 
 * out of natural language commands entirely offline, with zero SLM usage.
 */
object SlotExtractor {
    
    suspend fun extract(text: String): Map<String, Any> {
        return try {
            val extractor = EntityExtraction.getClient(
                EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH).build()
            )
            // ML Kit requires downloading the language model on first use if not bundled
            extractor.downloadModelIfNeeded().await()
            
            val params = EntityExtractionParams.Builder(text)
                .setPreferredLocale(Locale.ENGLISH)
                .build()
                
            val annotations = extractor.annotate(params).await()
            val result = mutableMapOf<String, Any>()
            
            for (annotation in annotations) {
                for (entity in annotation.entities) {
                    when (entity.type) {
                        Entity.TYPE_DATE_TIME -> {
                            val dt = entity.asDateTimeEntity()
                            dt?.timestampMillis?.let { result["timestamp"] = it }
                        }
                        Entity.TYPE_PHONE -> result["phone"] = annotation.annotatedText
                        Entity.TYPE_MONEY -> result["money"] = annotation.annotatedText
                        Entity.TYPE_EMAIL -> result["email"] = annotation.annotatedText
                        // You can add more mappings here. Note that pure durations 
                        // might need custom regex if ML Kit doesn't capture exactly what we need, 
                        // but ML Kit covers dates/times very well.
                    }
                }
            }
            
            // Basic regex for timer durations if ML Kit misses it
            if (!result.containsKey("seconds")) {
                val minMatch = Regex("(\\d+)\\s*(min|minute)").find(text)
                val secMatch = Regex("(\\d+)\\s*(sec|second)").find(text)
                val hourMatch = Regex("(\\d+)\\s*(hr|hour)").find(text)
                
                var totalSeconds = 0
                if (hourMatch != null) totalSeconds += hourMatch.groupValues[1].toInt() * 3600
                if (minMatch != null) totalSeconds += minMatch.groupValues[1].toInt() * 60
                if (secMatch != null) totalSeconds += secMatch.groupValues[1].toInt()
                
                if (totalSeconds > 0) {
                    result["seconds"] = totalSeconds
                }
            }
            
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
