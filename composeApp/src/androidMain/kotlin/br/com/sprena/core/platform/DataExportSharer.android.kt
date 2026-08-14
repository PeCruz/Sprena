package br.com.sprena.core.platform

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

private const val EXPORT_DIR = "exports"
private const val CHOOSER_TITLE = "Exportar meus dados"

@Composable
actual fun rememberDataExportSharer(): (ExportPayload) -> Unit {
    val context = LocalContext.current

    return remember(context) {
        { payload ->
            val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }

            // Um export por vez: o arquivo anterior contém CPF e telefone em claro e não
            // tem motivo para sobreviver ao seguinte. `allowBackup=false` (F1.1) já
            // impede que o cache vá para backup.
            dir.listFiles()?.forEach { it.delete() }

            val file = File(dir, payload.fileName).apply { writeText(payload.content) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

            val send =
                Intent(Intent.ACTION_SEND).apply {
                    type = payload.mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

            // O chooser é componente do sistema, então não precisa de `<queries>` no
            // manifest. FLAG_ACTIVITY_NEW_TASK porque o Context pode não ser o da Activity.
            context.startActivity(
                Intent.createChooser(send, CHOOSER_TITLE).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }
}
