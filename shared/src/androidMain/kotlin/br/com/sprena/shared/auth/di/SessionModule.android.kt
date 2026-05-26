package br.com.sprena.shared.auth.di

import br.com.sprena.shared.auth.session.EncryptedSessionStore
import br.com.sprena.shared.auth.session.SessionStore
import br.com.sprena.shared.core.time.Clock
import br.com.sprena.shared.core.time.SystemClock
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun sessionModule(): Module =
    module {
        single<Clock> { SystemClock() }
        single<SessionStore> { EncryptedSessionStore(context = androidContext(), logger = get()) }
    }
