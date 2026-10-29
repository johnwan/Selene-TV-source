package org.moontechlab.selenetv.remote

import android.content.Context
import okhttp3.tls.HeldCertificate
import java.io.File
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.TimeUnit

object RemoteTls {
    const val ALIAS = "selene-tv"
    data class Identity(val store: KeyStore, val password: String)
    fun load(context: Context): Identity {
        val folder = File(context.noBackupFilesDir, "phone-control").apply { mkdirs() }
        val passwordFile = File(folder, "tls-password")
        val password = if (passwordFile.exists()) passwordFile.readText() else UUID.randomUUID().toString().also { passwordFile.writeText(it) }
        val storeFile = File(folder, "identity.p12")
        val store = KeyStore.getInstance("PKCS12")
        if (storeFile.exists()) storeFile.inputStream().use { store.load(it, password.toCharArray()) }
        else {
            val cert = HeldCertificate.Builder().rsa2048().commonName("Selene-TV").duration(3650, TimeUnit.DAYS).build()
            store.load(null, password.toCharArray())
            store.setKeyEntry(ALIAS, cert.keyPair.private, password.toCharArray(), arrayOf(cert.certificate))
            val temporary = File(folder, "identity.tmp")
            temporary.outputStream().use { store.store(it, password.toCharArray()) }
            check(temporary.renameTo(storeFile))
        }
        return Identity(store, password)
    }
}
