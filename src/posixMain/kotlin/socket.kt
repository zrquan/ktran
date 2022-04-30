import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.toKString
import platform.posix.*

data class Socket(val fd: Int, val addr: sockaddr_in)

expect fun Socket.peernameWithPort(): String

expect class NSocket(hostname: String? = null, port: Int) {
    val hostname: String?
    val port: Int
    val address: sockaddr_in
    val fd: Int

    fun isServer(): Boolean

    fun socketAddressV4(): sockaddr_in

    fun Int.listen(backlog: Int = 50): Int

    fun sockaddr_in.bind(fd: Int): Int

    fun accept(): Socket

    fun connect(): Socket
}

expect fun select(
    nfds: Int,
    readfds: CValuesRef<fd_set>?,
    writefds: CValuesRef<fd_set>?,
    exceptfds: CValuesRef<fd_set>?,
    timeout: CValuesRef<timeval>?
): Boolean

expect fun recv(
    fd: Int,
    buf: CValuesRef<*>?,
    n: size_t,
    flags: Int
): ssize_t

expect fun send(
    fd: Int,
    buf: CValuesRef<*>?,
    n: size_t,
    flags: Int
): ssize_t

inline fun Int.ensureResult(op: String, predicate: (Int) -> Boolean): Int {
    if (!predicate(this)) {
        throw Error("[x] $op: ${strerror(posix_errno())!!.toKString()}")
    }
    return this
}
