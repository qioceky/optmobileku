package id.optmobileku.shizuku

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel

/**
 * Binder manual (tanpa AIDL) untuk ShellService yang berjalan sebagai
 * Shizuku user-service (proses terpisah, UID shell 2000).
 *
 * Dipilih manual karena toolchain AIDL tidak terpicu di build CI proyek
 * ini (pola yang sama dengan RuangApung, terbukti jalan di lapangan).
 *
 * Kontrak [exec]: menjalankan "sh -c <cmd>", mengembalikan seluruh
 * output (stdout+stderr tergabung) DITAMBAH penanda akhir
 * "\n<<<EXIT:<kode>>>" agar pemanggil tahu exit code-nya.
 */
interface IShellService : IInterface {

    fun exec(cmd: String): String

    abstract class Stub : Binder(), IShellService {

        companion object {
            const val DESCRIPTOR = "id.optmobileku.IShellService"
            const val TRANSACTION_exec = IBinder.FIRST_CALL_TRANSACTION + 0

            fun asInterface(binder: IBinder?): IShellService? {
                if (binder == null) return null
                val local = binder.queryLocalInterface(DESCRIPTOR)
                if (local is IShellService) return local
                return Proxy(binder)
            }
        }

        init {
            attachInterface(this, DESCRIPTOR)
        }

        override fun asBinder(): IBinder = this

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            when (code) {
                INTERFACE_TRANSACTION -> {
                    reply?.writeString(DESCRIPTOR)
                    return true
                }
                TRANSACTION_exec -> {
                    data.enforceInterface(DESCRIPTOR)
                    val result = exec(data.readString() ?: "")
                    reply?.writeNoException()
                    reply?.writeString(result)
                    return true
                }
            }
            return super.onTransact(code, data, reply, flags)
        }

        private class Proxy(private val remote: IBinder) : IShellService {
            override fun asBinder(): IBinder = remote

            override fun exec(cmd: String): String {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                return try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    data.writeString(cmd)
                    remote.transact(TRANSACTION_exec, data, reply, 0)
                    reply.readException()
                    reply.readString() ?: ""
                } finally {
                    data.recycle()
                    reply.recycle()
                }
            }
        }
    }
}
