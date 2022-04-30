import kotlin.native.concurrent.Future
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.native.concurrent.freeze

fun background(block: () -> Unit) {
    val future = worker.execute(TransferMode.SAFE, { block.freeze() }) {
        it()
    }
    collectFutures.add(future)
}

private val worker = Worker.start()
private val collectFutures = mutableListOf<Future<*>>()

fun teardownThreading() {
    collectFutures.forEach { it.result }
    worker.requestTermination()
}
