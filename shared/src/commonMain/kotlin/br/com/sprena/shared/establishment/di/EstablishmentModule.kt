package br.com.sprena.shared.establishment.di

import br.com.sprena.shared.establishment.domain.usecase.GetEstablishmentUseCase
import br.com.sprena.shared.establishment.domain.usecase.LinkMemberByCpfUseCase
import br.com.sprena.shared.establishment.domain.usecase.ObserveEstablishmentMembersUseCase
import br.com.sprena.shared.establishment.domain.usecase.ObserveEstablishmentsUseCase
import br.com.sprena.shared.establishment.domain.usecase.ObserveMyEstablishmentsUseCase
import br.com.sprena.shared.establishment.domain.usecase.SaveEstablishmentUseCase
import br.com.sprena.shared.establishment.domain.usecase.SelectActiveEstablishmentUseCase
import br.com.sprena.shared.establishment.domain.usecase.SetEstablishmentActiveUseCase
import org.koin.dsl.module

/**
 * Use cases de estabelecimento (F1.7.1).
 *
 * Só use cases: os repositórios dependem de Firebase e são ligados no `platformModule` do
 * Android, como manda a divisão do projeto — `shared/commonMain` não conhece Firebase.
 */
fun establishmentModule() =
    module {
        factory { SaveEstablishmentUseCase(repository = get(), logger = get()) }
        factory { ObserveEstablishmentsUseCase(repository = get()) }
        factory { GetEstablishmentUseCase(repository = get()) }
        factory { SetEstablishmentActiveUseCase(repository = get()) }
        factory { ObserveEstablishmentMembersUseCase(repository = get()) }
        factory { LinkMemberByCpfUseCase(repository = get()) }
        factory {
            ObserveMyEstablishmentsUseCase(
                memberships = get(),
                establishments = get(),
                logger = get(),
            )
        }
        factory {
            SelectActiveEstablishmentUseCase(
                memberships = get(),
                activeEstablishment = get(),
                logger = get(),
            )
        }
    }
