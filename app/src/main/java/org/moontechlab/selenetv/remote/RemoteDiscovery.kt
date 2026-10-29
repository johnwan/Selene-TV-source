package org.moontechlab.selenetv.remote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

class RemoteDiscovery(context: Context) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var registered = false
    private val listener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(info: NsdServiceInfo) { registered = true }
        override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) { registered = false }
        override fun onServiceUnregistered(info: NsdServiceInfo) { registered = false }
        override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) {}
    }
    fun register(id: String, name: String, port: Int) {
        nsd.registerService(NsdServiceInfo().apply {
            serviceType = "_selene-tv._tcp."
            serviceName = name
            this.port = port
            setAttribute("id", id); setAttribute("name", name); setAttribute("version", "1")
        }, NsdManager.PROTOCOL_DNS_SD, listener)
    }
    fun close() { if (registered) runCatching { nsd.unregisterService(listener) } }
}
