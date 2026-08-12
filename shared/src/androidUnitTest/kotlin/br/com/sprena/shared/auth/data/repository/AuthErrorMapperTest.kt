package br.com.sprena.shared.auth.data.repository

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthErrorMapperTest {
    @Test
    fun `usuario invalido vira mensagem unica anti-enumeracao`() {
        val e = FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "no user")

        assertEquals("Email ou senha incorretos", mapAuthError(e))
    }

    @Test
    fun `email malformado vira mensagem especifica`() {
        val e = FirebaseAuthInvalidCredentialsException("ERROR_INVALID_EMAIL", "bad email")

        assertEquals("Email inválido", mapAuthError(e))
    }

    @Test
    fun `senha errada nao revela que o email existe`() {
        val e = FirebaseAuthInvalidCredentialsException("ERROR_WRONG_PASSWORD", "bad password")

        assertEquals("Email ou senha incorretos", mapAuthError(e))
    }

    @Test
    fun `falha de rede vira aviso de conexao`() {
        assertEquals("Sem conexão. Verifique a internet", mapAuthError(FirebaseNetworkException("offline")))
    }

    @Test
    fun `conta desativada instrui a contatar admin`() {
        val e = FirebaseAuthException("ERROR_USER_DISABLED", "disabled")

        assertEquals("Conta desativada. Contate o administrador", mapAuthError(e))
    }

    @Test
    fun `rate limit instrui a esperar`() {
        val e = FirebaseAuthException("ERROR_TOO_MANY_REQUESTS", "too many")

        assertEquals("Muitas tentativas. Tente em alguns minutos", mapAuthError(e))
    }

    @Test
    fun `erro de auth desconhecido cai no generico`() {
        val e = FirebaseAuthException("ERROR_SOMETHING_NEW", "?")

        assertEquals("Erro de autenticação", mapAuthError(e))
    }

    // F1.4: antes destes casos, PERMISSION_DENIED nas Security Rules caia no
    // generico "Erro de autenticação" — depois de o Firebase Auth ja ter aceito
    // a senha. Diagnostico so era possivel pelo logcat.
    @Test
    fun `permissao negada pelas rules instrui a contatar admin`() {
        val e = firestoreException(FirebaseFirestoreException.Code.PERMISSION_DENIED)

        assertEquals("Conta sem permissão de acesso. Contate o administrador", mapAuthError(e))
    }

    @Test
    fun `firestore indisponivel vira aviso de conexao`() {
        val e = firestoreException(FirebaseFirestoreException.Code.UNAVAILABLE)

        assertEquals("Sem conexão. Verifique a internet", mapAuthError(e))
    }

    // F1.4b: com App Check em enforcement, um token de atestação ausente ou
    // recusado derruba a leitura de `users/{uid}` com UNAUTHENTICATED. Antes
    // deste caso a mensagem era "Erro ao carregar seu perfil", que manda o
    // usuário (e o suporte) investigar o lugar errado — o perfil está intacto,
    // quem foi recusado foi o app.
    @Test
    fun `token de atestacao recusado aponta para o app e nao para o perfil`() {
        val e = firestoreException(FirebaseFirestoreException.Code.UNAUTHENTICATED)

        assertEquals("Não foi possível validar o app neste dispositivo. Atualize e tente de novo", mapAuthError(e))
    }

    @Test
    fun `outra falha do firestore aponta para o perfil`() {
        val e = firestoreException(FirebaseFirestoreException.Code.DATA_LOSS)

        assertEquals("Erro ao carregar seu perfil", mapAuthError(e))
    }

    @Test
    fun `excecao fora do dominio firebase cai no generico`() {
        assertEquals("Erro de autenticação", mapAuthError(IllegalStateException("boom")))
    }

    @Test
    fun `diagnostico expoe o code do firestore para o log`() {
        val e = firestoreException(FirebaseFirestoreException.Code.PERMISSION_DENIED)

        assertEquals(" code=PERMISSION_DENIED", errorDiagnostics(e))
    }

    @Test
    fun `diagnostico expoe o errorCode do auth`() {
        val e = FirebaseAuthException("ERROR_USER_DISABLED", "disabled")

        assertEquals(" code=ERROR_USER_DISABLED", errorDiagnostics(e))
    }

    @Test
    fun `diagnostico e vazio para excecao sem code`() {
        assertEquals("", errorDiagnostics(IllegalStateException("boom")))
    }

    private fun firestoreException(code: FirebaseFirestoreException.Code) = FirebaseFirestoreException(code.name, code)
}
