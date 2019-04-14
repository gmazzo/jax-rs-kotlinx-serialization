package com.jakewharton.rs.kotlinx.serialization

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jboss.resteasy.core.NoMessageBodyWriterFoundFailure
import java.lang.UnsupportedOperationException
import javax.ws.rs.ApplicationPath
import javax.ws.rs.GET
import javax.ws.rs.NotAcceptableException
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.WebApplicationException
import javax.ws.rs.core.Application
import javax.ws.rs.core.HttpHeaders
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.ext.ExceptionMapper
import javax.ws.rs.ext.Provider

val jsonMessageBodyReader = Json.asMessageBodyReader(MediaType.APPLICATION_JSON_TYPE)

val jsonMessageBodyWriter = Json.asMessageBodyWriter(MediaType.APPLICATION_JSON_TYPE)

val testUsersList = listOf(User(1, "John"), User(2, "Mary"))

class TestApp : Application() {

    override fun getSingletons() = setOf(jsonMessageBodyReader, jsonMessageBodyWriter)

    override fun getClasses() = setOf(TestResource::class.java, ErrorMapper::class.java)

}

@Path("users")
@Produces(MediaType.APPLICATION_JSON)
class TestResource {

    @GET
    fun list(): List<User> = testUsersList

    @GET
    @Path("{id}")
    fun getById(@PathParam("id") id: Int): User? = testUsersList.find { it.id == id }

    @GET
    @Path("fail")
    fun fail(): Nothing = throw UnsupportedOperationException()

}

@Provider
@Produces(MediaType.APPLICATION_JSON)
class ErrorMapper : ExceptionMapper<Exception> {

    override fun toResponse(exception: Exception): Response = when (exception) {
        is NotAcceptableException -> exception.response
        is WebApplicationException -> Response.fromResponse(exception.response)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .entity(ErrorMessage(exception.response.status, exception.localizedMessage))
                .build()
        else -> Response.serverError()
                .entity(ErrorMessage(Response.Status.INTERNAL_SERVER_ERROR.statusCode, exception.localizedMessage))
                .build()
    }.also { exception.printStackTrace() }

}

@Serializable
data class User(val id: Int, val name: String)

@Serializable
data class ErrorMessage(val errorCode: Int, val message: String?)
