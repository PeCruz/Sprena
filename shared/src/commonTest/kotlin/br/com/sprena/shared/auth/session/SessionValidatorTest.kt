package br.com.sprena.shared.auth.session

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionValidatorTest {
    private val ttl = 24L * 60L * 60L * 1000L // 24h

    @Test
    fun `not expired when delta is zero`() {
        val now = 1_700_000_000_000L
        assertFalse(SessionValidator.isExpired(lastLoginEpochMillis = now, nowEpochMillis = now, ttlMillis = ttl))
    }

    @Test
    fun `not expired when delta is one millisecond before ttl`() {
        val last = 1_700_000_000_000L
        val now = last + ttl - 1L
        assertFalse(SessionValidator.isExpired(last, now, ttl))
    }

    @Test
    fun `expired when delta equals ttl exactly`() {
        val last = 1_700_000_000_000L
        val now = last + ttl
        assertTrue(SessionValidator.isExpired(last, now, ttl))
    }

    @Test
    fun `expired when delta greater than ttl`() {
        val last = 1_700_000_000_000L
        val now = last + ttl + 1L
        assertTrue(SessionValidator.isExpired(last, now, ttl))
    }

    @Test
    fun `expired when lastLogin is in the future (clock skew)`() {
        val now = 1_700_000_000_000L
        val last = now + 5_000L
        assertTrue(SessionValidator.isExpired(last, now, ttl))
    }

    @Test
    fun `default ttl is 24 hours`() {
        val last = 1_700_000_000_000L
        val almost24h = last + (24L * 60L * 60L * 1000L) - 1L
        assertFalse(SessionValidator.isExpired(last, almost24h))
    }
}
