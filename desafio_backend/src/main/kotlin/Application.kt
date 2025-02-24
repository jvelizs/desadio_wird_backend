/*
* Archivo Main donde se llaman a los distintos modulos que se utilizan dentro de la aplicación
* */

package com.todo
import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureWeatherUpdater()
    configureRouting()
}
