package br.com.sprena.core.platform

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberFilePicker(onFilePicked: (PickedFile) -> Unit): () -> Unit {
    val context = LocalContext.current

    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri: Uri? ->
            if (uri != null) {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                val nameIndex =
                    cursor?.getColumnIndex(
                        android.provider.OpenableColumns.DISPLAY_NAME,
                    ) ?: -1
                val sizeIndex =
                    cursor?.getColumnIndex(
                        android.provider.OpenableColumns.SIZE,
                    ) ?: -1

                var fileName = "arquivo"
                var fileSize = 0L

                if (cursor != null && cursor.moveToFirst()) {
                    if (nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex) ?: "arquivo"
                    }
                    if (sizeIndex >= 0) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
                cursor?.close()

                onFilePicked(PickedFile(name = fileName, sizeBytes = fileSize))
            }
        }

    return {
        launcher.launch(arrayOf("*/*"))
    }
}
