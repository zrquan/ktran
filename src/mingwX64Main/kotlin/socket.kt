import kotlinx.cinterop.CValuesRef
import platform.posix.*

actual fun Socket.peernameWithPort(): String {
    TODO()
}

actual class NSocket actual constructor(
    actual val hostname: String?,
    actual val port: Int
) {
    actual val address: sockaddr_in
        get() = TODO("Not yet implemented")
    actual val fd: Int
        get() = TODO("Not yet implemented")

    actual fun isServer(): Boolean = hostname == null

    actual fun socketAddressV4(): sockaddr_in {
        TODO("Not yet implemented")
    }

    actual fun Int.listen(backlog: Int): Int {
        TODO("Not yet implemented")
    }

    actual fun sockaddr_in.bind(fd: Int): Int {
        TODO("Not yet implemented")
    }

    actual fun accept(): Socket {
        TODO("Not yet implemented")
    }

    actual fun connect(): Socket {
        TODO("Not yet implemented")
    }
}

actual fun select(
    nfds: Int,
    readfds: CValuesRef<fd_set>?,
    writefds: CValuesRef<fd_set>?,
    exceptfds: CValuesRef<fd_set>?,
    timeout: CValuesRef<timeval>?
): Boolean {
    TODO("Not yet implemented")
}

actual fun recv(
    fd: Int,
    buf: CValuesRef<*>?,
    n: size_t,
    flags: Int
): ssize_t {
    TODO("Not yet implemented")
}

actual fun send(
    fd: Int,
    buf: CValuesRef<*>?,
    n: size_t,
    flags: Int
): ssize_t {
    TODO("Not yet implemented")
}
