package br.com.sprena.shared.auth.data.repository

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestoreException

/**
 * Traduz exceções do Firebase para mensagens em PT-BR exibíveis ao usuário.
 *
 * Duas regras que valem para qualquer ramo novo:
 *  - **Anti-enumeração**: usuário inexistente e senha errada compartilham a mesma
 *    mensagem — senão a tela de login vira oráculo de "este email tem conta".
 *  - **Sem PII**: a mensagem nunca ecoa email, uid ou o texto cru da exceção.
 */
internal fun mapAuthError(e: Throwable): String =
    when (e) {
        is FirebaseAuthInvalidUserException -> "Email ou senha incorretos"
        is FirebaseAuthInvalidCredentialsException -> {
            if (e.errorCode == "ERROR_INVALID_EMAIL") {
                "Email inválido"
            } else {
                "Email ou senha incorretos"
            }
        }
        is FirebaseNetworkException -> "Sem conexão. Verifique a internet"
        is FirebaseAuthException ->
            when (e.errorCode) {
                "ERROR_USER_DISABLED" -> "Conta desativada. Contate o administrador"
                "ERROR_TOO_MANY_REQUESTS" -> "Muitas tentativas. Tente em alguns minutos"
                else -> "Erro de autenticação"
            }
        // Falha ao ler `users/{uid}` — acontece DEPOIS de o Auth aceitar a senha.
        // PERMISSION_DENIED aqui é quase sempre Security Rules (F1.4) não
        // publicadas ou negando o doc de perfil.
        is FirebaseFirestoreException ->
            when (e.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    "Conta sem permissão de acesso. Contate o administrador"
                FirebaseFirestoreException.Code.UNAVAILABLE ->
                    "Sem conexão. Verifique a internet"
                else -> "Erro ao carregar seu perfil"
            }
        else -> "Erro de autenticação"
    }

/**
 * Sufixo de diagnóstico para o log — nunca para a UI.
 *
 * Só o código do Firestore/Auth, que não é PII e é o dado que faltava para
 * distinguir "senha errada" de "rules negaram o perfil" no logcat.
 */
internal fun errorDiagnostics(e: Throwable): String =
    when (e) {
        is FirebaseFirestoreException -> " code=${e.code.name}"
        is FirebaseAuthException -> " code=${e.errorCode}"
        else -> ""
    }
