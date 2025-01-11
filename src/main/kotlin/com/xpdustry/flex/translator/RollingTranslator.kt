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

import com.xpdustry.flex.FlexScope
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

public class RollingTranslator(public val translators: List<Translator>, public val fallback: Translator) :
    Translator {
    private val cursor = AtomicInteger(0)

public override fun translate(text: String, source: Locale, target: Locale): CompletableFuture<String> =
        FlexScope.future {
            val cursor = cursor.getAndUpdate { if (it + 1 < translators.size) it + 1 else 0 }
            for (i in translators.indices) {
                val translator = translators[(cursor + i) % translators.size]
                try {
                    return@future translator.translate(text, source, target).await()
                } catch (e: Exception) {
                    logger.debug("Translator {} failed", translator.javaClass.simpleName, e)
                }
            }
            return@future fallback.translate(text, source, target).await()
        }

    private companion object {
        private val logger = LoggerFactory.getLogger(RollingTranslator::class.java)
    }
}
