package br.com.sprena.presentation.establishment.edit

import br.com.sprena.shared.core.mvi.UiIntent

sealed interface EstablishmentEditIntent : UiIntent {
    data class NameChanged(
        val value: String,
    ) : EstablishmentEditIntent

    data class CnpjChanged(
        val value: String,
    ) : EstablishmentEditIntent

    data class RazaoSocialChanged(
        val value: String,
    ) : EstablishmentEditIntent

    data class PhoneChanged(
        val value: String,
    ) : EstablishmentEditIntent

    data class EmailChanged(
        val value: String,
    ) : EstablishmentEditIntent

    data class ActiveChanged(
        val value: Boolean,
    ) : EstablishmentEditIntent

    data class StreetChanged(
        val value: String,
    ) : EstablishmentEditIntent

    data class NumberChanged(
        val value: String,
    ) : EstablishmentEditIntent

    data class ComplementChanged(
        val value: String,
    ) : EstablishmentEditIntent

    data class DistrictChanged(
        val value: String,
    ) : EstablishmentEditIntent

    data class CityChanged(
        val value: String,
    ) : EstablishmentEditIntent

    data class StateChanged(
        val value: String,
    ) : EstablishmentEditIntent

    data class ZipCodeChanged(
        val value: String,
    ) : EstablishmentEditIntent

    data object SaveClicked : EstablishmentEditIntent

    data object Retry : EstablishmentEditIntent
}
