# Política de Privacidade — onde vive o texto

O texto vigente **não** está neste arquivo. Ele é a fonte única embarcada no app:

`composeApp/src/commonMain/composeResources/files/privacy-policy.md`

Motivo: o aceite grava a versão exata do texto que o usuário leu. Manter uma cópia aqui criaria
duas verdades e a chance de divergirem.

## Como alterar a política

1. Editar `composeApp/src/commonMain/composeResources/files/privacy-policy.md`
2. Atualizar a linha `Versão AAAA-MM-DD` no topo do texto
3. Atualizar `PrivacyPolicy.VERSION` em
   `shared/src/commonMain/kotlin/br/com/sprena/shared/privacy/domain/model/PrivacyPolicy.kt`
   com o mesmo valor
4. Publicar o app — todos os usuários reaceitam no próximo acesso, e o aceite anterior fica
   preservado em `user_consents/{uid}/history/{id}`, com **id automático**. O id não é a versão
   da política: com `history/{policyVersion}`, reaceitar a mesma versão seria `set` sobre doc
   existente, que em Rules conta como `update` e é negado, derrubando o batch atômico e
   prendendo o usuário no gate fail-closed (corrigido em `cb08a8e`).

> **Sequenciamento.** O bump e a coleta do dado novo têm que sair na **mesma release**. Bumpar
> antes de coletar descreve algo que ainda não acontece; coletar antes de bumpar é o problema de
> LGPD de verdade — o usuário teria consentido com um texto que não menciona o dado.

## Publicação como URL pública

A Play Store exige uma URL pública de política de privacidade no listing. No release, publicar o
mesmo arquivo (por exemplo via GitHub Pages) e apontar o listing para ele. Esse passo é de release,
não de build — por isso não há automação no repositório.
