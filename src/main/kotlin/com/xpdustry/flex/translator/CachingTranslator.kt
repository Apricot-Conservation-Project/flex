/*
 * This file is part of Flex. An advanced text processing library for Mindustry plugins.
 *
 * MIT License
 *
 * Copyright (c) 2024 Xpdustry
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.xpdustry.flex.translator

import com.github.benmanes.caffeine.cache.AsyncCacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Expiry
import com.github.benmanes.caffeine.cache.Ticker
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import kotlin.time.Duration

public class CachingTranslator(
    public val translator: Translator,
    public val maximumSize: Int,
    public val successRetention: Duration,
    public val failureRetention: Duration,
    ticker: Ticker = Ticker.systemTicker(),
) : Translator {
    init {
        require(maximumSize > 0) { "maximumSize must be positive" }
        require(successRetention >= Duration.ZERO) { "successRetention must be non-negative" }
        require(failureRetention >= Duration.ZERO) { "failureRetention must be non-negative" }
    }

    private val cache =
        Caffeine.newBuilder()
            .expireAfter(TranslationExpiry())
            .maximumSize(maximumSize.toLong())
            .ticker(ticker)
            .buildAsync(TranslationLoader(translator))

    override fun translate(text: String, source: Locale, target: Locale): CompletableFuture<String> =
        cache
            .get(TranslationKey(text, Locale.forLanguageTag(source.language), Locale.forLanguageTag(target.language)))
            .thenCompose {
                when (it) {
                    is TranslationResult.Success -> CompletableFuture.completedFuture(it.translation)
                    is TranslationResult.Failure -> CompletableFuture.failedFuture(it.throwable)
                }
            }

    private class TranslationLoader(private val translator: Translator) :
        AsyncCacheLoader<TranslationKey, TranslationResult> {
        override fun asyncLoad(key: TranslationKey, executor: Executor): CompletableFuture<TranslationResult> =
            translator
                .translate(key.text, key.source, key.target)
                .thenApply<TranslationResult>(TranslationResult::Success)
                .exceptionally(TranslationResult::Failure)
    }

    private inner class TranslationExpiry : Expiry<TranslationKey, TranslationResult> {
        override fun expireAfterCreate(key: TranslationKey, value: TranslationResult, currentTime: Long) =
            when (value) {
                is TranslationResult.Success -> successRetention.inWholeNanoseconds
                is TranslationResult.Failure -> failureRetention.inWholeNanoseconds
            }

        override fun expireAfterUpdate(
            key: TranslationKey,
            value: TranslationResult,
            currentTime: Long,
            currentDuration: Long,
        ) =
            when (value) {
                is TranslationResult.Success -> successRetention.inWholeNanoseconds
                is TranslationResult.Failure -> failureRetention.inWholeNanoseconds
            }

        override fun expireAfterRead(
            key: TranslationKey,
            value: TranslationResult,
            currentTime: Long,
            currentDuration: Long,
        ) =
            when (value) {
                is TranslationResult.Success -> successRetention.inWholeNanoseconds
                is TranslationResult.Failure -> currentDuration
            }
    }
}
