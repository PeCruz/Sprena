package br.com.sprena.shared.kanban.domain.validation

import br.com.sprena.shared.core.validation.ValidationResult

object TaskValidator {
    const val NAME_MAX_LENGTH: Int = 50
    const val DESCRIPTION_MAX_LENGTH: Int = 3000
    const val COMMENT_MAX_LENGTH: Int = 2000
    const val ATTACHMENT_MAX_BYTES: Long = 100L * 1024L * 1024L
    const val PRIORITY_MIN: Int = 1
    const val PRIORITY_MAX: Int = 5

    fun validateName(name: String): ValidationResult {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> ValidationResult.invalid("Nome é obrigatório")
            name.length > NAME_MAX_LENGTH ->
                ValidationResult.invalid("Máximo de $NAME_MAX_LENGTH caracteres")
            else -> ValidationResult.Valid
        }
    }

    fun validatePriority(priority: Int?): ValidationResult =
        when {
            priority == null -> ValidationResult.invalid("Prioridade é obrigatória")
            priority < PRIORITY_MIN || priority > PRIORITY_MAX ->
                ValidationResult.invalid("Prioridade deve ser entre $PRIORITY_MIN e $PRIORITY_MAX")
            else -> ValidationResult.Valid
        }

    fun validateDescription(description: String?): ValidationResult =
        when {
            description == null || description.isEmpty() -> ValidationResult.Valid
            description.length > DESCRIPTION_MAX_LENGTH ->
                ValidationResult.invalid("Máximo de $DESCRIPTION_MAX_LENGTH caracteres")
            else -> ValidationResult.Valid
        }

    fun validateComment(comment: String?): ValidationResult =
        when {
            comment == null || comment.isEmpty() -> ValidationResult.Valid
            comment.length > COMMENT_MAX_LENGTH ->
                ValidationResult.invalid("Máximo de $COMMENT_MAX_LENGTH caracteres")
            else -> ValidationResult.Valid
        }

    fun validateAttachmentSize(sizeBytes: Long): ValidationResult =
        when {
            sizeBytes > ATTACHMENT_MAX_BYTES ->
                ValidationResult.invalid("Anexo excede 100 MB")
            else -> ValidationResult.Valid
        }

    fun validateEndDate(
        endEpochDay: Long?,
        todayEpochDay: Long,
    ): ValidationResult =
        when {
            endEpochDay == null -> ValidationResult.invalid("Data de fim é obrigatória")
            endEpochDay < todayEpochDay ->
                ValidationResult.invalid("Data de fim deve ser hoje ou no futuro")
            else -> ValidationResult.Valid
        }

    fun validateStartDate(
        startEpochDay: Long,
        todayEpochDay: Long,
    ): ValidationResult =
        when {
            startEpochDay != todayEpochDay ->
                ValidationResult.invalid("Data de início deve ser o dia atual")
            else -> ValidationResult.Valid
        }
}
