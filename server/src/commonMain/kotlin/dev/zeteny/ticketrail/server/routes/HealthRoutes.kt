package dev.zeteny.ticketrail.server.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route


fun Route.healthRoutes(){
    get("/health") {
        call.respond(HttpStatusCode.OK)
    }
}