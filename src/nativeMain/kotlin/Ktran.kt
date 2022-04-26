import kotlinx.cinterop.*
import platform.darwin.inet_addr
import platform.darwin.inet_ntoa
import platform.posix.*

fun main(vararg args: String) {
    if (args.isEmpty()) {
        printUsage()
        return
    }

    when (args[0]) {
        "-l", "--listen" -> {
            println("[+] Enable listen mode")
            _listen(args[1].toInt(), args[2].toInt())
        }
        "-t", "--tran" -> {
            println("[+] Enable transfer mode")
            _transfer(args[1].toInt(), args[2], args[3].toInt())
        }
        "-s", "--slave" -> {
            println("[+] Enable slave mode")
            _slave(args[1], args[2].toInt(), args[3], args[4].toInt())
        }
        else -> printUsage()
    }
}

fun printUsage() = println(
    """
    [Usage]
      ktran.kexe --<listen|tran|slave> <option>
      
    [Option]
      -l, --listen <ConnectPort> <TransmitPort>
      -t, --tran <ConnectPort> <TransmitHost> <TransmitPort>
      -s, --slave <ConnectHost> <ConnectPort> <TransmitHost> <TransmitPort>
    """.trimIndent()
)

data class Socket(val fd: Int, val addr: sockaddr_in) {
    fun peernameWithPort() = memScoped {
        val sa = alloc<sockaddr_in>()
        val len = alloc<socklen_tVar>().apply {
            value = sizeOf<sockaddr_in>().convert()
        }
        getpeername(fd, sa.ptr.reinterpret(), len.ptr)
        val host = inet_ntoa(sa.sin_addr.readValue())?.toKString()
        val port = sa.sin_port.toString()
        "$host:$port"
    }
}

class Server(val port: Int, val backlog: Int = 50) {
    private val serverAddr = socketAddressV4()
    private var listenFd = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP)
        .ensureResult("create socket") { it != -1 }

    init {
//        memScoped {
//            val flag = alloc<IntVar>()
//            flag.value = 1
//            setsockopt(listenFd, SOL_SOCKET, SO_REUSEADDR, flag.ptr, sizeOf<IntVar>().convert())
//        }
        serverAddr.bind(listenFd)
    }

    private fun socketAddressV4() = memScoped {
        alloc<sockaddr_in>().apply {
            sin_family = AF_INET.convert()
            sin_port = posix_htons(port.toShort()).convert()
            sin_addr.s_addr = INADDR_ANY.convert()
        }
    }

    private fun Int.listen(backlog: Int) =
        listen(this, backlog)
            .ensureResult("listen") { it == 0 }

    private fun sockaddr_in.bind(fd: Int): Int =
        bind(fd, this.ptr.reinterpret(), sizeOf<sockaddr_in>().convert())
            .ensureResult("bind") { it >= 0 }
            .also { fd.listen(backlog) }

    fun accept() = Socket(
        accept(listenFd, null, null),
        serverAddr
    ).also { println("[+] Connection from ${it.peernameWithPort()} on port $port") }
}

class Client(val hostname: String, val port: Int) {
    private val remoteAddr = socketAddressV4()
    private var clientFd = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP)
        .ensureResult("create socket") { it != -1 }

    private fun socketAddressV4() = memScoped {
//        val host = gethostbyname(hostname) ?: throw Exception("Unknown host: $hostname")

        alloc<sockaddr_in>().apply {
            sin_family = AF_INET.convert()
            sin_port = posix_htons(port.toShort()).convert()
            // pointed 属性返回 CPointer<T> 类型中 T 对应的 lvalue
            sin_addr.s_addr = inet_addr(hostname)
        }
    }

    fun connect(): Socket {
        connect(clientFd, remoteAddr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert())
            .ensureResult("connect") { it == 0 }
        return Socket(clientFd, remoteAddr)
            .also { println("[+] Connect to ${it.peernameWithPort()}") }
    }
}

inline fun Int.ensureResult(op: String, predicate: (Int) -> Boolean): Int {
    if (!predicate(this)) {
        throw Error("[x] $op: ${strerror(posix_errno())!!.toKString()}")
    }
    return this
}

private fun _listen(srcPort: Int, dstPort: Int) {
    val srcSocket = Server(srcPort)
    val dstSocket = Server(dstPort)
    while (true) {
        val src = srcSocket.accept()
        val dst = dstSocket.accept()
        if (src.fd == -1 || dst.fd == -1) {
            println("[x] Accept socket failed, retry 5s later")
            sleep(5)
            continue
        }
        background { transData(src, dst) }
    }
}

private fun _transfer(srcPort: Int, dstHost: String, dstPort: Int) {
    val srcSocket = Server(srcPort)
    while (true) {
        val src = srcSocket.accept()
        val dst = Client(dstHost, dstPort).connect()
        if (src.fd == -1 || dst.fd == -1) {
            println("[x] Accept socket failed, retry 5s later")
            sleep(5)
            continue
        }
        background { transData(src, dst) }
    }
}

private fun _slave(srcHost: String, srcPort: Int, dstHost: String, dstPort: Int) {
    while (true) {
        val src = Client(srcHost, srcPort).connect()
        val dst = Client(dstHost, dstPort).connect()
        if (src.fd == -1 || dst.fd == -1) {
            println("[x] Accept socket failed, retry 5s later")
            sleep(5)
            continue
        }
        background { transData(src, dst) }
    }
}

/**
 * 在两个 socket 间传输数据
 */
fun transData(so1: Socket, so2: Socket) {
    memScoped {
        val socks = arrayListOf(so1, so2)

        val checkList = alloc<fd_set>()

        val buffer = ByteArray(8192)
        var totalLen = 0L
        buffer.usePinned { pinned ->
            while (true) {
                // reset fd_set
                posix_FD_ZERO(checkList.ptr)
                posix_FD_SET(so1.fd, checkList.ptr)
                posix_FD_SET(so2.fd, checkList.ptr)
                if (select(FD_SETSIZE, checkList.ptr, null, null, null) <= 0) continue

                for (i in 0..1) {
                    if (posix_FD_ISSET(socks[i].fd, checkList.ptr) == 0) continue

                    val dataLen = recv(socks[i].fd, pinned.addressOf(0), buffer.size.convert(), 0)

                    if (dataLen > 0) {
                        totalLen += dataLen
                        val other = socks[(i + 1) % 2]
                        send(other.fd, pinned.addressOf(0), dataLen.convert(), 0)
                        println("[+] Send: ${socks[i].peernameWithPort()} -> ${other.peernameWithPort()}, $dataLen bytes")
                    } else {
                        println("[-] Connection shutdown. Total: $totalLen bytes")
                        return@usePinned
                    }
                }
            }
        }
    }
}
