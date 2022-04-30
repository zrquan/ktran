import kotlinx.cinterop.*
import platform.posix.*

fun main(vararg args: String) {
    if (args.isEmpty()) {
        printUsage()
        return
    }

    when (args[0]) {
        "-l", "--listen" -> {
            println("[+] Enable listen mode".green())
            _listen(args[1].toInt(), args[2].toInt())
        }
        "-t", "--tran" -> {
            println("[+] Enable transfer mode".green())
            _transfer(args[1].toInt(), args[2], args[3].toInt())
        }
        "-s", "--slave" -> {
            println("[+] Enable slave mode".green())
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

private fun _listen(srcPort: Int, dstPort: Int) {
    val srcSocket = NSocket(port = srcPort)
    val dstSocket = NSocket(port = dstPort)
    while (true) {
        val src = srcSocket.accept()
        val dst = dstSocket.accept()
        if (src.fd == -1 || dst.fd == -1) {
            println("[x] Accept socket failed, retry 5s later".red())
            sleep(5)
            continue
        }
        background { transData(src, dst) }
    }
}

private fun _transfer(srcPort: Int, dstHost: String, dstPort: Int) {
    val srcSocket = NSocket(port = srcPort)
    while (true) {
        val src = srcSocket.accept()
        val dst = NSocket(dstHost, dstPort).connect()
        if (src.fd == -1 || dst.fd == -1) {
            println("[x] Accept socket failed, retry 5s later".red())
            sleep(5)
            continue
        }
        background { transData(src, dst) }
    }
}

private fun _slave(srcHost: String, srcPort: Int, dstHost: String, dstPort: Int) {
    while (true) {
        val src = NSocket(srcHost, srcPort).connect()
        val dst = NSocket(dstHost, dstPort).connect()
        if (src.fd == -1 || dst.fd == -1) {
            println("[x] Accept socket failed, retry 5s later".red())
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
                if (!select(FD_SETSIZE, checkList.ptr, null, null, null)) continue

                for (i in 0..1) {
                    if (posix_FD_ISSET(socks[i].fd, checkList.ptr) == 0) continue

                    val dataLen = recv(socks[i].fd, pinned.addressOf(0), buffer.size.convert(), 0)

                    if (dataLen > 0) {
                        totalLen += dataLen
                        val other = socks[(i + 1) % 2]
                        send(other.fd, pinned.addressOf(0), dataLen.convert(), 0)
                        println("[+] Send: ${socks[i].peernameWithPort()} -> ${other.peernameWithPort()}, $dataLen bytes".blue())
                    } else {
                        println("[-] Connection shutdown. Total: $totalLen bytes".red())
                        return@usePinned
                    }
                }
            }
        }
    }
}

fun String.red() = "\u001B[31m$this\u001B[0m"
fun String.blue() = "\u001B[34m$this\u001B[0m"
fun String.green() = "\u001B[32m$this\u001B[0m"
