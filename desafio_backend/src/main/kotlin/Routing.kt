/*
* En este archivo encontraremos la función para devolver la información almacenada en el caché respecto a una
* localidad. Además aca se encuentra el enpoint para obtenerlo mediante una solicitud HTTP, el cual tiene como ruta
* "weather/{location}", siendo location la localidad que se desea buscar.
* Cabe recalcar que las localidades que se tienen permitido pasar son las que se encuentran dentro del archivo YML en
* el recurso del proyecto, se puede ver el funcionamiento dentro de la documentación.
* */
package com.todo

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.yaml.snakeyaml.Yaml
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import java.io.InputStream


fun Application.configureRouting() {
    routing {
        // Se define el endpoint para obtener la información respecto a una localidad.
        get("weather/{location}") {
            // Se llama a la función que se encarga de devolver la data almacenada en redis.
            val infolocation = getFromCache(call.parameters["location"] ?: "")
            // Se devuelve la información como json.
            call.respondText(infolocation, ContentType.Application.Json)
        }
    }

}

// Función para obtener desde caché.
private fun getFromCache(location: String): String {
    // Se establece la conexión con redis para obtener la información del caché.
    val jedisPool = JedisPool(JedisPoolConfig(), dotenv["REDIS_HOST"], dotenv["REDIS_PORT"].toInt())

    // Se lee el archivo YML para obtener las llaves asociadas en el caché.
    val inputStream: InputStream = {}.javaClass.getResourceAsStream("/cities.yml")
        ?: throw IllegalArgumentException("Archivo YAML no encontrado")

    val yaml = Yaml()
    // Se carga la información dentro del YML.
    val data: Map<String, Map<String, String>> = yaml.load(inputStream)

    // Se carga las localidades disponibles en el archivo YML.
    val cities: Map<String, String> = data["cities"] as Map<String, String>

    // Se obtiene la llave asociada con respecto a la localidad solicitada, en caso de no estar se arroja el error:
    // "Localidad no encontrada".
    val cacheKey = cities[location]?: throw IllegalArgumentException("Localidad no encontrada")
    jedisPool.resource.use { jedis ->
        try {
            // Se intenta recuperar la información dentro de redis.
            val cachedData = jedis.get("weather_${cacheKey.trim()}")
            return cachedData
        }catch (e:Exception){
            // En caso de ocurrir un error se arroja.
            throw e
        }

    }
}