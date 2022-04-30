import kotlinx.cinterop.*
import platform.darwin.inet_addr
import platform.darwin.inet_ntoa
import platform.posix.*

actual fun Socket.peernameWithPort() = memScoped {
    val sa = alloc<sockaddr_in>()
    val len = alloc<socklen_tVar>().apply {
        value = sizeOf<sockaddr_in>().convert()
    }
    getpeername(fd, sa.ptr.reinterpret(), len.ptr)
    val host = inet_ntoa(sa.sin_addr.readValue())?.toKString()
    val port = sa.sin_port.toString()
    "$host:$port"
}

actual class NSocket actual constructor(
    actual val hostname: String?,
    actual val port: Int
) {
    actual val address: sockaddr_in = socketAddressV4()
    actual val fd: Int = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP)
        .ensureResult("create socket") { it != -1 }

    init {
        // 允许重复绑定端口
        ktran.socket.enable_reuse(fd)
        if (isServer()) address.bind(fd)
    }

    actual fun isServer(): Boolean = hostname == null

    actual fun socketAddressV4() = memScoped {
        alloc<sockaddr_in>().apply {
            sin_family = AF_INET.convert()
            sin_port = posix_htons(port.toShort()).convert()
            sin_addr.s_addr = if (isServer()) INADDR_ANY.convert() else inet_addr(hostname)
        }
    }

    actual fun Int.listen(backlog: Int) =
        listen(this, backlog)
            .ensureResult("listen") { it == 0 }

    actual fun sockaddr_in.bind(fd: Int) =
        bind(fd, this.ptr.reinterpret(), sizeOf<sockaddr_in>().convert())
            .ensureResult("bind") { it >= 0 }
            .also { fd.listen(50) }

    actual fun accept() = Socket(
        accept(fd, null, null),
        address
    ).also { println("[+] Connection from ${it.peernameWithPort()} on port $port") }

    actual fun connect(): Socket {
        connect(fd, address.ptr.reinterpret(), sizeOf<sockaddr_in>().convert())
            .ensureResult("connect") { it == 0 }
        return Socket(fd, address)
            .also { println("[+] Connect to ${it.peernameWithPort()}") }
    }
}

actual fun select(
    nfds: Int,
    readfds: CValuesRef<fd_set>?,
    writefds: CValuesRef<fd_set>?,
    exceptfds: CValuesRef<fd_set>?,
    timeout: CValuesRef<timeval>?
): Boolean = platform.posix.select(nfds, readfds, writefds, exceptfds, timeout) > 0

actual fun recv(
    fd: Int,
    buf: CValuesRef<*>?,
    n: size_t,
    flags: Int
): ssize_t = platform.posix.recv(fd, buf, n, flags)

actual fun send(
    fd: Int,
    buf: CValuesRef<*>?,
    n: size_t,
    flags: Int

): ssize_t = platform.posix.send(fd, buf, n, flags)
