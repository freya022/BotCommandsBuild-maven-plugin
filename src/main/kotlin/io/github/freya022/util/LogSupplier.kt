package io.github.freya022.util

import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.logging.Log
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class LogSupplier(private val mojo: AbstractMojo) : ReadOnlyProperty<Any?, Log> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): Log = mojo.log
}