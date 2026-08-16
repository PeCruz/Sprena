package br.com.sprena.shared.establishment.domain.model

/**
 * Um estabelecimento onde o usuário atual tem vínculo, junto do papel que exerce lá.
 *
 * É o que alimenta o seletor global: o nome vem do documento do estabelecimento e o papel
 * vem do vínculo, duas leituras distintas. O papel **não** é denormalizado no
 * estabelecimento nem o nome no vínculo — os dois mudam por caminhos diferentes (o ADM
 * renomeia o estabelecimento; uma callable altera o papel), e copiar qualquer um dos dois
 * criaria um par que envelhece sem ninguém perceber.
 */
data class MyEstablishment(
    val establishment: Establishment,
    val role: MemberRole,
)
