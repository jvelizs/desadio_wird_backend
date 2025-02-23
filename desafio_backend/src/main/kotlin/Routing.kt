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
        get("weather/{location}") {
            val infolocation = getFromCache(call.parameters["location"] ?: "")
            call.respondText(infolocation, ContentType.Application.Json)
        }
    }
}

private fun getFromCache(location: String): String {
    val jedisPool = JedisPool(JedisPoolConfig(), dotenv["REDIS_HOST"], dotenv["REDIS_PORT"].toInt())
    val inputStream: InputStream = {}.javaClass.getResourceAsStream("/cities.yml")
        ?: throw IllegalArgumentException("Archivo YAML no encontrado")
    val yaml = Yaml()
    val data: Map<String, Map<String, String>> = yaml.load(inputStream)
    val cities: Map<String, String> = data["cities"] as Map<String, String>
    val cacheKey = cities[location]?: throw IllegalArgumentException("Localidad no encontrada")
    jedisPool.resource.use { jedis ->
        try {
            val cachedData = jedis.get("weather_${cacheKey.trim()}")
            return cachedData
        }catch (e:Exception){
            throw e
        }

    }
}