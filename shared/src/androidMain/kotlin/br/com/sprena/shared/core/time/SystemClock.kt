package br.com.sprena.shared.core.time

class SystemClock : Clock {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
}
