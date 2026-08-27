package br.com.sprena.core.ui.mask

import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * As máscaras deste arquivo não tinham teste algum até aqui, e o modo de falha delas não é
 * cosmético: o Compose lança exceção quando o `OffsetMapping` devolve um índice fora do texto
 * transformado, então um erro de aritmética aqui derruba o formulário ao posicionar o cursor —
 * e só no dispositivo, ao digitar. O caso do mapeamento total abaixo é o que cobre isso.
 */
class CnpjMaskTransformationTest {
    private val transformation = CnpjMaskTransformation()

    private fun mask(digits: String) = transformation.filter(AnnotatedString(digits)).text.text

    @Test
    fun `formata o CNPJ completo`() {
        assertEquals("11.222.333/0001-81", mask("11222333000181"))
    }

    @Test
    fun `formata parcialmente enquanto se digita`() {
        assertEquals("", mask(""))
        assertEquals("1", mask("1"))
        assertEquals("11", mask("11"))
        assertEquals("11.2", mask("112"))
        assertEquals("11.222", mask("11222"))
        assertEquals("11.222.3", mask("112223"))
        assertEquals("11.222.333", mask("11222333"))
        assertEquals("11.222.333/0", mask("112223330"))
        assertEquals("11.222.333/0001", mask("112223330001"))
        assertEquals("11.222.333/0001-8", mask("1122233300018"))
    }

    @Test
    fun `descarta o que nao e digito e o excedente`() {
        assertEquals("11.222.333/0001-81", mask("11.222.333/0001-81"))
        // Um 15º dígito não pode empurrar o campo além da máscara.
        assertEquals("11.222.333/0001-81", mask("112223330001819"))
    }

    @Test
    fun `mapeamento de offset nunca sai do texto transformado`() {
        for (length in 0..14) {
            val digits = "1".repeat(length)
            val transformed = transformation.filter(AnnotatedString(digits))
            val masked = transformed.text.text
            val mapping = transformed.offsetMapping

            for (offset in 0..length) {
                val forward = mapping.originalToTransformed(offset)
                assertTrue(
                    forward in 0..masked.length,
                    "originalToTransformed($offset) devolveu $forward para \"$masked\"",
                )
            }
            for (offset in 0..masked.length) {
                val back = mapping.transformedToOriginal(offset)
                assertTrue(
                    back in 0..length,
                    "transformedToOriginal($offset) devolveu $back para $length dígitos",
                )
            }
        }
    }

    @Test
    fun `ida e volta preserva a posicao do cursor`() {
        val digits = "11222333000181"
        val mapping = transformation.filter(AnnotatedString(digits)).offsetMapping

        for (offset in 0..digits.length) {
            assertEquals(
                offset,
                mapping.transformedToOriginal(mapping.originalToTransformed(offset)),
                "cursor perdeu a posição em $offset",
            )
        }
    }
}
