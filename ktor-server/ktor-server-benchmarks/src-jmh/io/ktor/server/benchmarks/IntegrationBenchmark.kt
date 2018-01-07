package io.ktor.server.benchmarks

import ch.qos.logback.classic.Level
import io.ktor.application.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import io.ktor.server.netty.*
import org.openjdk.jmh.annotations.*
import org.slf4j.*
import org.slf4j.Logger
import java.io.*
import java.util.concurrent.*


@State(Scope.Benchmark)
abstract class IntegrationBenchmark {
    private val coreDirectory = File("../ktor-server-core").absoluteFile.normalize()
    private val packageName = IntegrationBenchmark::class.java.`package`.name
    private val classFileName = IntegrationBenchmark::class.simpleName!! + ".class"
    private val smallFile = File(coreDirectory, "build.gradle")
    private val largeFile = File(coreDirectory, "build").walkTopDown().maxDepth(2).filter {
        it.name.startsWith("ktor-server-core") && it.name.endsWith("SNAPSHOT.jar")
    }.single()

    lateinit var server: ApplicationEngine
    private val httpClient = OkHttpBenchmarkClient()

    private val port = 5678

    abstract fun createServer(port: Int, main: Application.() -> Unit): ApplicationEngine

    @Setup
    fun configureServer() {
        val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger
        val okContent = TextContent("OK", ContentType.Text.Plain, HttpStatusCode.OK)
        root.level = Level.ERROR
        server = createServer(port) {
            routing {
                get("/sayOK") {
                    call.respond(okContent)
                }
                get("/thinkOK") {
                    call.respondText("OK")
                }
                get("/query") {
                    val parameters = call.parameters
                    val message = parameters["message"]
                            ?: throw IllegalArgumentException("GET request should have `message` parameter")
                    call.respondText(message)
                }
                static {
                    resource("jarfile", "String.class", "java.lang")
                    resource("regularClasspathFile", classFileName, packageName)
                    file("smallFile", smallFile)
                    file("largeFile", largeFile)
                }
                get("/smallFileSync") {
                    call.respond(smallFile.readBytes())
                }
                get("/largeFileSync") {
                    call.respond(largeFile.readBytes())
                }
            }
        }
        server.start()
        Thread.sleep(500)
    }

    @TearDown
    fun shutdownServer() {
        server.stop(100, 5000, TimeUnit.MILLISECONDS)
    }

    @Setup
    fun configureClient() {
        httpClient.setup()
    }

    @TearDown
    fun shutdownClient() {
        httpClient.shutdown()
    }

    private fun load(url: String) {
        httpClient.load(url)
    }

    @Benchmark
    fun sayOK() {
        load("http://localhost:$port/sayOK")
    }

    @Benchmark
    fun query() {
        load("http://localhost:$port/query?utm_source=Google&utm_medium=cpc&utm_campaign=ok%2B+plus&utm_content=obshie&message=OK")
    }

    @Benchmark
    fun thinkOK() {
        load("http://localhost:$port/thinkOK")
    }

    @Benchmark
    fun jarfile() {
        load("http://localhost:$port/jarfile")
    }

    @Benchmark
    fun regularClasspathFile() {
        load("http://localhost:$port/regularClasspathFile")
    }

    @Benchmark
    fun smallFile() {
        load("http://localhost:$port/smallFile")
    }

    @Benchmark
    fun smallFileSync() {
        load("http://localhost:$port/smallFileSync")
    }

    @Benchmark
    fun largeFile() {
        load("http://localhost:$port/largeFile")
    }

    @Benchmark
    fun largeFileSync() {
        load("http://localhost:$port/largeFileSync")
    }
}

class NettyIntegrationBenchmark : IntegrationBenchmark() {
    override fun createServer(port: Int, main: Application.() -> Unit): ApplicationEngine {
        return embeddedServer(Netty, port, module = main)
    }
}

class JettyIntegrationBenchmark : IntegrationBenchmark() {
    override fun createServer(port: Int, main: Application.() -> Unit): ApplicationEngine {
        return embeddedServer(Jetty, port, module = main)
    }
}

class CIOIntegrationBenchmark : IntegrationBenchmark() {
    override fun createServer(port: Int, main: Application.() -> Unit): ApplicationEngine {
        return embeddedServer(CIO, port, module = main)
    }
}

/*
NOTE: Results on Ilya's MacBook Pro, rebooted without any extra programs running

$ gradle runBenchmark -PbenchmarkName=IntegrationBenchmark

Benchmark                                        Mode  Cnt   Score   Error   Units
CIOIntegrationBenchmark.jarfile                 thrpt   20   9.968 ± 0.287  ops/ms
CIOIntegrationBenchmark.largeFile               thrpt   20   0.707 ± 0.011  ops/ms
CIOIntegrationBenchmark.largeFileSync           thrpt   20   0.591 ± 0.011  ops/ms
CIOIntegrationBenchmark.query                   thrpt   20  44.976 ± 0.735  ops/ms
CIOIntegrationBenchmark.regularClasspathFile    thrpt   20  16.668 ± 0.328  ops/ms
CIOIntegrationBenchmark.sayOK                   thrpt   20  49.641 ± 0.950  ops/ms
CIOIntegrationBenchmark.smallFile               thrpt   20  32.927 ± 0.688  ops/ms
CIOIntegrationBenchmark.smallFileSync           thrpt   20  36.226 ± 0.794  ops/ms
CIOIntegrationBenchmark.thinkOK                 thrpt   20  49.225 ± 0.945  ops/ms

JettyIntegrationBenchmark.jarfile               thrpt   20  10.312 ± 0.492  ops/ms
JettyIntegrationBenchmark.largeFile             thrpt   20   1.107 ± 0.044  ops/ms
JettyIntegrationBenchmark.largeFileSync         thrpt   20   1.320 ± 0.034  ops/ms
JettyIntegrationBenchmark.query                 thrpt   20  21.624 ± 1.142  ops/ms
JettyIntegrationBenchmark.regularClasspathFile  thrpt   20  15.485 ± 1.305  ops/ms
JettyIntegrationBenchmark.sayOK                 thrpt   20  30.280 ± 1.956  ops/ms
JettyIntegrationBenchmark.smallFile             thrpt   20  24.572 ± 2.082  ops/ms
JettyIntegrationBenchmark.smallFileSync         thrpt   20  20.284 ± 1.305  ops/ms
JettyIntegrationBenchmark.thinkOK               thrpt   20  27.102 ± 1.414  ops/ms

NettyIntegrationBenchmark.jarfile               thrpt   20  11.005 ± 0.367  ops/ms
NettyIntegrationBenchmark.largeFile             thrpt   20   0.790 ± 0.027  ops/ms
NettyIntegrationBenchmark.largeFileSync         thrpt   20   2.613 ± 0.038  ops/ms
NettyIntegrationBenchmark.query                 thrpt   20  43.593 ± 0.967  ops/ms
NettyIntegrationBenchmark.regularClasspathFile  thrpt   20  17.545 ± 0.335  ops/ms
NettyIntegrationBenchmark.sayOK                 thrpt   20  48.655 ± 0.927  ops/ms
NettyIntegrationBenchmark.smallFile             thrpt   20  31.566 ± 0.545  ops/ms
NettyIntegrationBenchmark.smallFileSync         thrpt   20  34.855 ± 0.901  ops/ms
NettyIntegrationBenchmark.thinkOK               thrpt   20  50.996 ± 0.771  ops/ms
*/

fun main(args: Array<String>) {
    benchmark(args) {
        threads = 32
        run<JettyIntegrationBenchmark>()
        run<NettyIntegrationBenchmark>()
        run<CIOIntegrationBenchmark>()
    }
}


