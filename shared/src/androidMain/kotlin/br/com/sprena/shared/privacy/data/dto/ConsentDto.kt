package br.com.sprena.shared.privacy.data.dto

import br.com.sprena.shared.privacy.domain.model.ConsentRecord
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

/**
 * Mapeia o doc `user_consents/{uid}` para o domínio.
 *
 * `acceptedAt` é gravado com `FieldValue.serverTimestamp()`; na leitura imediata
 * após a escrita ele pode vir null (o servidor ainda não resolveu) — nesse caso
 * cai para 0, o que é irrelevante: o gate só compara `policyVersion`.
 */
object ConsentDto {
    fun fromSnapshot(snapshot: DocumentSnapshot): ConsentRecord? {
        val uid = snapshot.getString("uid") ?: return null
        val version = snapshot.getString("policyVersion") ?: return null
        val acceptedAt = snapshot.get("acceptedAt") as? Timestamp
        return ConsentRecord(
            uid = uid,
            policyVersion = version,
            acceptedAtEpochMillis = acceptedAt?.toDate()?.time ?: 0L,
        )
    }
}
