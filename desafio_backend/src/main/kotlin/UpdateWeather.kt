package com.todo

import io.ktor.server.application.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json.Default.parseToJsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.minutes
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import org.yaml.snakeyaml.Yaml
import java.io.InputStream
import io.github.cdimascio.dotenv.dotenv

val dotenv = dotenv()

fun Application.configureWeatherUpdater() {


    val jedisPool = JedisPool(JedisPoolConfig().apply {
        maxTotal = 10
        maxIdle = 5
        minIdle = 2
    }, dotenv["REDIS_HOST"], dotenv["REDIS_PORT"].toInt())
    val client = HttpClient(CIO)

    monitor.subscribe(ApplicationStopping) {
        client.close()
        jedisPool.close()
    }
    val cities = loadCities()
    weatherUpdater(client, jedisPool, cities)
}

private fun Application.weatherUpdater(client: HttpClient, jedisPool: JedisPool, cities: List<String>) {
    CoroutineScope(Dispatchers.IO).launch {
        while (isActive) {
             cities.map { city ->
                async {
                    jedisPool.resource.use { jedis ->
                        weatherUpdaterByCities(client, city, jedis)
                    }
                }
            }.awaitAll()
            delay(5.minutes)
        }
    }
}



private suspend fun Application.weatherUpdaterByCities(client: HttpClient, city: String, jedis: Jedis): JsonObject {
    val errorKey = "weather_error_$city"
    var attempts = 0
    val maxAttempts = 3
    while(attempts < maxAttempts) {
        try {

            if (Math.random() < 0.2) {
                throw Exception("The API Request Failed in $city")
            }
            val weather: HttpResponse = client.get(dotenv["WEATHER_URL"]) {
                header("accept", "application/json")
                parameter("location", city)
                parameter("apikey", dotenv["API_KEY"])
            }
            val result = weather.bodyAsText()

            val prettyJson: JsonObject = parseToJsonElement(result).jsonObject
            val code = prettyJson["code"]?.jsonPrimitive?.int

            if (code != null) {
                val errorMessage = prettyJson["message"].toString()
                jedis.setex(errorKey, 600, errorMessage)
                return prettyJson
            }

            val cacheKey = "weather_${city.trim()}"
            jedis.setex(cacheKey, 600, prettyJson.toString())
            return prettyJson
        } catch (e: Exception) {
            attempts++
            if (attempts >= maxAttempts) {
                jedis.setex(e.toString(), 600, e.message)
                throw e
            }
            val delayTime = (1000L * attempts)
            println("Reintentando ($attempts/$maxAttempts) en $city")
            delay(delayTime)
        }
    }
    throw Exception("Unexpected error in $city")
}

private fun loadCities(): List<String> {
    val inputStream: InputStream = {}.javaClass.getResourceAsStream("/cities.yml")
        ?: throw IllegalArgumentException("Archivo YAML no encontrado")

    val yaml = Yaml()
    val data: Map<String, Map<String, String>> = yaml.load(inputStream)

    return data["cities"]?.values?.toList() ?: emptyList()
}

