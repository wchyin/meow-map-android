package cn.edu.meowmap

import cn.edu.meowmap.data.ApiFailure
import cn.edu.meowmap.data.OriginalApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.thread

class OriginalApiTest {
    private data class Request(val line: String, val headers: Map<String, String>, val bytes: ByteArray)
    private fun serve(server: ServerSocket, status: Int): CompletableFuture<Request> {
        val result = CompletableFuture<Request>()
        thread(isDaemon = true) {
            try {
                server.accept().use { socket ->
                    socket.soTimeout = 5000
                    val input = socket.getInputStream().buffered()
                    fun line(): String {
                        val output = StringBuilder()
                        while (true) {
                            val c = input.read(); if (c < 0 || c == 10) break
                            if (c != 13) output.append(c.toChar())
                        }
                        return output.toString()
                    }
                    val first = line(); val headers = mutableMapOf<String, String>()
                    while (true) {
                        val header = line(); if (header.isEmpty()) break
                        headers[header.substringBefore(':').lowercase()] = header.substringAfter(':').trim()
                    }
                    val bytes = ByteArray(headers["content-length"]?.toInt() ?: 0)
                    var offset = 0
                    while (offset < bytes.size) { val count = input.read(bytes, offset, bytes.size-offset); check(count > 0); offset += count }
                    val body = if (status == 200) "{\"ok\":true}" else "{\"error\":\"expired\"}"
                    socket.getOutputStream().use { output ->
                        output.write("HTTP/1.1 $status Result\r\nContent-Type: application/json\r\nContent-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n$body".toByteArray())
                    }
                    result.complete(Request(first, headers, bytes))
                }
            } catch (error: Throwable) { result.completeExceptionally(error) }
        }
        return result
    }
    @Test fun binaryUploadUsesOriginalServerContract() = runBlocking {
        ServerSocket(0).use { server ->
            val captured = serve(server, 200)
            OriginalApi("http://127.0.0.1:${server.localPort}/meow-api") { "test-token" }
                .request("/uploads", "POST", byteArrayOf(1,2,3), true)
            val request = captured.get()
            assertEquals("POST /meow-api/uploads HTTP/1.1", request.line)
            assertEquals("application/octet-stream", request.headers["content-type"])
            assertEquals("Bearer test-token", request.headers["authorization"])
            assertArrayEquals(byteArrayOf(1,2,3), request.bytes)
        }
    }
    @Test fun authorizationFailureIsNotAnEmptyList() = runBlocking {
        ServerSocket(0).use { server ->
            serve(server, 401)
            try { OriginalApi("http://127.0.0.1:${server.localPort}") { null }.json("/records"); fail("Expected 401") }
            catch (failure: ApiFailure) { assertEquals(401, failure.status); assertEquals("expired", failure.message) }
        }
    }
}
