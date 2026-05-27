package br.com.sprena.shared.auth.di

import org.koin.core.module.Module

/** SessionStore impl é por plataforma. */
expect fun sessionModule(): Module
