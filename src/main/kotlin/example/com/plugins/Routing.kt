package example.com.plugins

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.random.Random
import kotlin.system.*
import io.github.cdimascio.dotenv.dotenv
import kotlinx.coroutines.runBlocking
import redis.clients.jedis.Jedis


fun Application.configureRouting() {
    routing {
        get("/weather/{city}") {
            if (call.parameters["city"] == null || !isValidCity(call.parameters["city"])) {
                call.respondText("Error: Invalid City", ContentType.Application.Json)
            }

            val redisClient = Jedis("localhost", 6379) // Connect to Redis server
            val city = call.parameters["city"] // Replace with dynamic value as needed
            val cacheKey = "weather_$city"

            // Check if data is cached in Redis
            val cachedResponse = redisClient.get(cacheKey)

            if (cachedResponse != null) {
                // If cached data exists, respond with cached data
                call.respondText(cachedResponse, ContentType.Application.Json)
            }

            try {
                val responseData = getWeatherByCity(city)

                redisClient.set(cacheKey, responseData)
                redisClient.expire(cacheKey, 300)

                call.respondText(responseData, ContentType.Application.Json)
            } catch (e: Exception) {
                val currentTimestamp: Double = (System.currentTimeMillis() / 1000).toDouble()
                redisClient.zadd(
                        city + "_errors",
                        currentTimestamp,
                        city + ":" + currentTimestamp + ":" + e.message
                )
                call.respondText("The API Request Failed", ContentType.Application.Json)
            }

            redisClient.close() // Close Redis connection
        }

        get("/cache/invalidate") {
            val redisClient = Jedis("localhost", 6379)
            redisClient.flushDB() // Clears all keys in the current database
            redisClient.close()
            call.respondText("Cache Invalidated")
        }
    }
}

fun getWeatherByCity(city: String?): String = runBlocking {
    val dotenv = dotenv()
    val key = dotenv["WEATHER_API_KEY"]
    val url = dotenv["WEATHER_API_URL"]
    val apiRoute = "$url?key=$key&q=$city&aqi=no"

    // Mocks failure in 20% of the requests
    mockRandomFailure()

    val client = configureClient()
    val response: HttpResponse =
            client.get(apiRoute) {
                headers { append(HttpHeaders.Accept, ContentType.Application.Json.toString()) }
            }
    response.bodyAsText()
}

fun mockRandomFailure() {
    // Mocks failure in 20% of the requests
    val errorMessages =
            arrayOf(
                    "API Limit Reached",
                    "Server is down",
                    "Not Found",
                    "Unauthorized",
            )
    if (Random.nextFloat() < 0.2) {
        // NextInt uses exclusive range
        throw Exception(errorMessages[Random.nextInt(0, 4)])
    }
}

fun configureClient(): HttpClient {
    return HttpClient(CIO) {
        expectSuccess = true
        install(HttpRequestRetry) { retryOnServerErrors(maxRetries = 3) }
    }
}

fun isValidCity(city: String?): Boolean {
    val validCities = arrayOf("london", "santiago", "zurich", "auckland", "sidney", "georgia")
    return city in validCities
}
