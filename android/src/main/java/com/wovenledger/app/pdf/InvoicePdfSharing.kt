package com.wovenledger.app.pdf

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.wovenledger.app.BuildConfig
import com.wovenledger.app.ui.components.formatMoney
import java.io.File

/**
 * Puts a rendered invoice somewhere Android's share sheet can reach it.
 *
 * The PDF goes in a cache subdirectory rather than external storage: no permission is
 * needed, the system reclaims it, and a `FileProvider` hands out a one-shot read grant
 * so WhatsApp or Gmail can pull the bytes without the file ever being world-readable.
 */
object InvoicePdfSharing {

    const val MIME_TYPE = "application/pdf"

    /** Cache subdirectory; must match the `<cache-path>` in res/xml/file_paths.xml. */
    private const val DIRECTORY = "invoices"

    private val AUTHORITY = "${BuildConfig.APPLICATION_ID}.fileprovider"

    /**
     * Renders [document] into the cache and returns the file.
     *
     * Named after the document number, because that filename is what the customer sees
     * in their chat or inbox — "SI-0015.pdf" says what it is; "share.pdf" does not.
     */
    fun write(context: Context, document: InvoiceDocument): File {
        val directory = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
        val file = File(directory, invoicePdfFileName(document.number))

        file.outputStream().use { InvoicePdfRenderer.render(document, it) }

        return file
    }

    /** ACTION_SEND with a read grant for [file]; the caller starts it from an Activity. */
    fun shareIntent(context: Context, file: File, document: InvoiceDocument): Intent {
        val uri = FileProvider.getUriForFile(context, AUTHORITY, file)

        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Invoice ${document.number}")
            putExtra(
                Intent.EXTRA_TEXT,
                "Invoice ${document.number} from ${document.company.name} — " +
                    "total ${formatMoney(document.total)}",
            )
            // Some targets read the grant off the clip data rather than the extra.
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return Intent.createChooser(send, "Share ${file.name}")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** Renders, then opens the share sheet. */
    fun share(context: Context, document: InvoiceDocument) {
        val file = write(context, document)

        context.startActivity(shareIntent(context, file, document))
    }
}
