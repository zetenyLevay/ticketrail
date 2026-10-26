package dev.zeteny.ticketrail.server.plugins

import dev.zeteny.ticketrail.sayHello
import dev.zeteny.ticketrail.server.routes.healthRoutes
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun Application.configureRouting(){
    routing {
        healthRoutes()
    }
}