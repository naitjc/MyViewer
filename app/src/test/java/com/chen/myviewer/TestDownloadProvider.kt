package com.chen.myviewer

import android.content.Context
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsProvider
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.io.IOException

/** A real DocumentsProvider protocol backed by an isolated test directory. */
internal class TestDownloadProvider : DocumentsProvider() {
    lateinit var directory: File
    var failWrites = false
    var interruptWrites = false
    var writable = true
    val tree: Uri get() = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "root")

    fun register(context: Context): TestDownloadProvider {
        directory = File(context.cacheDir, "test-folder-output").apply { deleteRecursively(); mkdirs() }
        attachInfo(context, ProviderInfo().apply {
            authority = AUTHORITY
            exported = true
            grantUriPermissions = true
            readPermission = "android.permission.MANAGE_DOCUMENTS"
            writePermission = "android.permission.MANAGE_DOCUMENTS"
        })
        // Robolectric sends legacy queries directly to providers. DocumentsProvider
        // accepts the Bundle query overload from Android O onward.
        val delegate = this
        val bridge = object : ContentProvider() {
            override fun onCreate() = true
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? =
                delegate.query(uri, projection, Bundle(), null)
            override fun getType(uri: Uri) = delegate.getType(uri)
            override fun call(method: String, arg: String?, extras: Bundle?): Bundle? = delegate.call(method, arg, extras)
            override fun openFile(uri: Uri, mode: String) = delegate.openFile(uri, mode)
            override fun insert(uri: Uri, values: ContentValues?): Uri? = error("Use DocumentsContract")
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        }
        bridge.attachInfo(context, ProviderInfo().apply { authority = AUTHORITY; exported = true; grantUriPermissions = true })
        ShadowContentResolver.registerProviderInternal(AUTHORITY, bridge)
        org.robolectric.Shadows.shadowOf(context as android.app.Application).grantPermissions("android.permission.MANAGE_DOCUMENTS")
        context.grantUriPermission(context.packageName, tree, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        return this
    }

    override fun onCreate() = true
    override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(projection ?: arrayOf(DocumentsContract.Root.COLUMN_ROOT_ID))
    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor = cursor(projection).apply { add(documentId) }
    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor = cursor(projection).apply {
        directory.listFiles().orEmpty().forEach { add(it.name) }
    }
    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        check(writable)
        check(File(directory, displayName).createNewFile())
        return displayName
    }
    override fun deleteDocument(documentId: String) { File(directory, documentId).delete() }
    override fun isChildDocument(parentDocumentId: String, documentId: String) = parentDocumentId == "root" && File(directory, documentId).isFile
    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        if (failWrites && mode.contains('w')) throw IOException("测试写入失败")
        if (interruptWrites && mode.contains('w')) Thread.currentThread().interrupt()
        return ParcelFileDescriptor.open(File(directory, documentId), ParcelFileDescriptor.parseMode(mode))
    }
    private fun cursor(projection: Array<out String>?) = MatrixCursor(projection ?: arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_FLAGS, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED))
    private fun MatrixCursor.add(id: String) {
        val file = if (id == "root") directory else File(directory, id)
        val values: Map<String, Any> = mapOf(
            Document.COLUMN_DOCUMENT_ID to id,
            Document.COLUMN_DISPLAY_NAME to file.name,
            Document.COLUMN_MIME_TYPE to if (id == "root") Document.MIME_TYPE_DIR else "application/octet-stream",
            Document.COLUMN_FLAGS to if (writable) Document.FLAG_DIR_SUPPORTS_CREATE or Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_DELETE else 0,
            Document.COLUMN_SIZE to file.length(),
            Document.COLUMN_LAST_MODIFIED to file.lastModified(),
        )
        addRow(columnNames.map { values[it] }.toTypedArray())
    }
    companion object { private const val AUTHORITY = "com.chen.myviewer.test.downloads" }
}
