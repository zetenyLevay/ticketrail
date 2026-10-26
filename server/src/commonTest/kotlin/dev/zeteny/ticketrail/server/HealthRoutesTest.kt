package dev.zeteny.ticketrail.server

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.*
import kotlin.test.*

class HealthRoutesTest {


    @Test
    fun testHealthCheck() = testApplication {
        application{module()}
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
    }
}