@file:JvmName("KotlinxSerializationMessageBodyReader")

package com.jakewharton.rs.kotlinx.serialization

import org.glassfish.hk2.utilities.binding.AbstractBinder
import org.glassfish.jersey.client.ClientConfig
import org.glassfish.jersey.netty.httpserver.NettyHttpContainerProvider
import org.glassfish.jersey.server.ResourceConfig
import org.jboss.resteasy.core.NoMessageBodyWriterFoundFailure
import org.jboss.resteasy.plugins.server.netty.NettyJaxrsServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.lang.IllegalStateException
import java.lang.RuntimeException
import java.net.ServerSocket
import java.net.URI
import javax.ws.rs.client.ClientBuilder
import javax.ws.rs.client.WebTarget
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.core.UriBuilder

/**
 * This test cover Serialization/Deserialization from both sides, by raising up a local JAX-RS server (which encodes JSON),
 * and consuming some APIs with a client (by decoding JSON).
 */
@RunWith(Parameterized::class)
class KotlinxSerializationMessageBodyITTest(private val provider: Provider) {

    private val client = ClientBuilder.newClient(ClientConfig(jsonMessageBodyReader, jsonMessageBodyWriter))

    private val port = ServerSocket(0).apply { close() }.localPort

    private val resourceUri = UriBuilder
            .fromUri("http://localhost:$port/")
            .path(TestResource::class.java)

    private lateinit var onStopServer: () -> Unit

    @Test
    fun testGetById() {
        val targetUser = testUsersList.first()

        val response: User? = client.target(resourceUri.path(targetUser.id.toString())).execute().entity()

        assertEquals(targetUser, response)
        assertTrue(targetUser !== response)
    }

    @Test
    fun testList() {
        val response: Array<User> = client.target(resourceUri).execute().entity()

        assertEquals(testUsersList, response.toList())
    }

    @Test
    fun testNotFound() {
        val response = client.target(resourceUri.path("unknown/api"))
                .execute(assureOk = false)

        assertEquals(Response.Status.NOT_FOUND, response.statusInfo)
        assertEquals(Response.Status.NOT_FOUND.statusCode, response.entity<ErrorMessage>().errorCode)
    }

    @Test
    fun testNotAcceptable() {
        val response = client.target(resourceUri)
                .execute(assureOk = false, accept = MediaType.APPLICATION_OCTET_STREAM_TYPE)

        assertEquals(Response.Status.NOT_ACCEPTABLE, response.statusInfo)
        assertFalse(response.hasEntity())
        assertNotEquals(MediaType.APPLICATION_JSON, response.mediaType)
    }

    @Test
    fun testExceptionThrown() {
        val response = client.target(resourceUri.path("fail"))
                .execute(assureOk = false)

        assertEquals(Response.Status.INTERNAL_SERVER_ERROR, response.statusInfo)
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.statusCode, response.entity<ErrorMessage>().errorCode)
    }

    @Before
    fun startServer() {
        onStopServer = provider.start(port)

        println("$provider server running at http://localhost:$port")
    }

    @After
    fun stopServer() {
        onStopServer()
    }

    companion object {

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data() = Provider.values().map {
            arrayOf(it)
        }

    }

    enum class Provider {

        JERSEY {
            override fun start(port: Int): () -> Unit = NettyHttpContainerProvider
                    .createHttp2Server(
                            URI.create("http://localhost:$port/"),
                            ResourceConfig.forApplication(TestApp()),
                            null)
                    .let { { it.close().await() } }
        },

        RESTEASY {
            override fun start(port: Int) = NettyJaxrsServer()
                    .apply {
                        deployment.application = TestApp()
                        this.port = port
                        start()
                    }::stop
        };

        abstract fun start(port: Int): () -> Unit

        override fun toString() = name.toLowerCase()

    }

    private fun WebTarget.execute(
            assureOk: Boolean = true,
            accept: MediaType = MediaType.APPLICATION_JSON_TYPE) = request()
            .accept(accept)
            .buildGet()
            .invoke()
            .apply { if (assureOk && status != Response.Status.OK.statusCode) throw ClientHttpException(uri, statusInfo) }

    private inline fun <reified T> Response.entity() = readEntity(T::class.java)

    class ClientHttpException(uri: URI, status: Response.StatusType)
        : RuntimeException("$uri ${status.statusCode} ${status.reasonPhrase}")

}
