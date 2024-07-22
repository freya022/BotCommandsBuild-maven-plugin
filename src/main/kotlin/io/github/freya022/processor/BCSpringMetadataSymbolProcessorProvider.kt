package io.github.freya022.processor

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import io.github.freya022.util.LogSupplier
import java.nio.file.Path

class BCSpringMetadataSymbolProcessorProvider(private val logSupplier: LogSupplier, private val writePath: Path) : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return BCSpringMetadataSymbolProcessor(logSupplier, writePath)
    }
}