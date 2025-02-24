/*
Este archivo corresponde a la función de obtener la información que nos entrega la api, a su vez guardar dicha información de manera efectiva
dentro del caché de redis.
 */

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

// Variable Global que nos permite hacer el llamado a las Variables de entorno
val dotenv = dotenv()

fun Application.configureWeatherUpdater() {
    // Se establece la comunicación con redis
    val jedisPool = JedisPool(JedisPoolConfig().apply {
        maxTotal = 10 // max de conexiones simultaneas
        maxIdle = 5 // max de comunicaciones inactivas
        minIdle = 2 // min de comunicaciones inactivas
    }, dotenv["REDIS_HOST"], dotenv["REDIS_PORT"].toInt())

    // Cliente que permite realizar solicitudes HTTP
    val client = HttpClient(CIO)

    // Cuando se detiene la aplicación se detiene ambas conexiones
    monitor.subscribe(ApplicationStopping) {
        client.close()
        jedisPool.close()
    }
    // LLamada a la función que nos permite obtener las localidades definidas en YML, entregando una lista de estas
    val cities = loadCities()

    // LLamada a la funcion que se encarga de hacer el update cada 5 min
    weatherUpdater(client, jedisPool, cities)
}

private fun weatherUpdater(client: HttpClient, jedisPool: JedisPool, cities: List<String>) {
    // Se establece como corrutina, de tal forma permite correr esta función en simultáneo junto a las demás establecidas.
    CoroutineScope(Dispatchers.IO).launch {
        // Mientras la corrutina está activa, se ejecuta la función que nos permite extraer la información.
        while (isActive) {
            // Se hace un mapeo de las ciudades entregadas a la función.
             cities.map { city ->
                async {
                    // Asincronicamente se van obteniendo la información respecto a las localidades
                    jedisPool.resource.use { jedis ->
                        weatherUpdaterByCities(client, city, jedis)
                    }
                }
            }.awaitAll() // Espera a terminar con todas las localidades.
            delay(5.minutes)
        }
    }
}


// Función que se encarga de obtener la información por medio de la api metereologica
private suspend fun weatherUpdaterByCities(client: HttpClient, city: String, jedis: Jedis) {

    // LLave para referenciar el error entregado por la api meteorologica dentro del caché respecto a la ciudad
    // Por ej: Cuando se ha realizado muchas solicitudes.
    val errorKey = "weather_error_$city"

    // La cantidad de intentos fallidos.
    var attempts = 0

    // La cantidad máxima de intentos.
    val maxAttempts = 3

    // Mientras los intentos fallidos no alcance el máximo, el servicio debe reintentar obtener la información.
    while(attempts < maxAttempts) {
        try {
            // Condicional para simular que la llamada a la api falla un 20% de las veces.
            if (Math.random() < 0.2) {
                // Se arroja el error en caso de fallar.
                throw Exception("The API Request Failed in $city")
            }

            // Variable la cual obtiene la información de la app meteorologica mediante una solicitud HTTP.
            val weather: HttpResponse = client.get(dotenv["WEATHER_URL"]) {
                header("accept", "application/json")
                parameter("location", city)
                parameter("apikey", dotenv["API_KEY"])
            }

            // Se recibe el cuerpo de la respuesta como un texto para su serialización a json.
            val result = weather.bodyAsText()

            // Se pasa el formato a JSON para mejor manejo de la información.
            val prettyJson: JsonObject = parseToJsonElement(result).jsonObject

            // Cuando a la api se le hace muchas consultas, devuelve un codigo y un mensaje
            val code = prettyJson["code"]?.jsonPrimitive?.int

            // Se pregunta por la presencia de "code"
            if (code != null) {
                val errorMessage = prettyJson["message"].toString()
                // Si se presenta code, entonces se guarda en redis con errorKey.
                jedis.setex(errorKey, 600, errorMessage)
            }

            // En caso de una solicitud exitosa, primero se asigna la key con la cual será referenciada la información
            val cacheKey = "weather_${city.trim()}"

            // Se guarda en redis la información con su key creada, en caso de ya existir se sobreescribe la información
            jedis.setex(cacheKey, 600, prettyJson.toString())

            // Se rompe el ciclo en caso de ser exitoso.
            break

        } catch (e: Exception) {
            // En caso de que ocurra un error ya sea por la simulación u otro tipo se suma uno a la cantidad de intentos
            attempts++

            // En caso de ser mayor al máximo se arroja el error y se guarda dentro de redis
            if (attempts >= maxAttempts) {
                // Se guarda el error correspondiente en redis
                jedis.setex(e.toString(), 600, e.message)
                throw e
            }
            // Se agrega un pequeño diley respecto a la cantidad de intentos, de 1[s], 2[s] e 3[s]
            val delayTime = (1000L * attempts)

            // Se coloca el mensaje de reintentos faltantes con respecto a la ciudad
            println("Reintentando ($attempts/$maxAttempts) en $city")
            delay(delayTime)
        }
    }
}

// Función que carga las localidades disponibles en el archivo YML
private fun loadCities(): List<String> {
    // Se obtiene la información como recurso del archivo YML, en caso de no encontrar el archivo se arroja un error
    val inputStream: InputStream = {}.javaClass.getResourceAsStream("/cities.yml")
        ?: throw IllegalArgumentException("Archivo YAML no encontrado")

    val yaml = Yaml()

    // Se carga el recurso del YML como una lista de string.
    val data: Map<String, Map<String, String>> = yaml.load(inputStream)

    // Se cargan todas las ciudades bajo la key cities.
    return data["cities"]?.values?.toList() ?: emptyList()
}

