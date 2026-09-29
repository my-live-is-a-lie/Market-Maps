package com.marketmaps.app.ui.map

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** نقطة على المسار بترتيب خط العرض ثم خط الطول. */
data class RoutePoint(val latitude: Double, val longitude: Double)

data class RoutePlan(
    val points: List<RoutePoint>,
    val distanceMeters: Double,
    val durationSeconds: Double
)

/** جلب مسار قيادة من خدمة OSRM العامة، مع إرجاع خط الطريق الفعلي لا خطاً مستقيماً. */
object RouteRepository {
    private const val BASE_URL = "https://router.project-osrm.org/route/v1/driving"

    suspend fun fetchDrivingRoute(
        originLatitude: Double,
        originLongitude: Double,
        destinationLatitude: Double,
        destinationLongitude: Double
    ): RoutePlan = withContext(Dispatchers.IO) {
        val coordinates = "${coordinate(originLongitude)},${coordinate(originLatitude)};" +
            "${coordinate(destinationLongitude)},${coordinate(destinationLatitude)}"
        val url = URL("$BASE_URL/$coordinates?overview=full&geometries=geojson&steps=false")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "MarketMaps/1.0 (Android)")
            setRequestProperty("Accept", "application/json")
        }

        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("تعذر الاتصال بخدمة الاتجاهات (${connection.responseCode})")
            }
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(response)
            if (root.optString("code") != "Ok") {
                throw IllegalStateException("لم يتم العثور على طريق بين الموقعين")
            }
            val route = root.optJSONArray("routes")?.optJSONObject(0)
                ?: throw IllegalStateException("لم يتم العثور على طريق بين الموقعين")
            val coordinatesArray = route.optJSONObject("geometry")?.optJSONArray("coordinates")
                ?: throw IllegalStateException("تعذر قراءة مسار الطريق")
            val points = buildList(coordinatesArray.length()) {
                for (index in 0 until coordinatesArray.length()) {
                    val coordinate = coordinatesArray.optJSONArray(index) ?: continue
                    if (coordinate.length() < 2) continue
                    val longitude = coordinate.optDouble(0, Double.NaN)
                    val latitude = coordinate.optDouble(1, Double.NaN)
                    if (latitude.isFinite() && longitude.isFinite()) {
                        add(RoutePoint(latitude, longitude))
                    }
                }
            }
            if (points.size < 2) throw IllegalStateException("المسار المستلم غير مكتمل")

            RoutePlan(
                points = points,
                distanceMeters = route.optDouble("distance", 0.0),
                durationSeconds = route.optDouble("duration", 0.0)
            )
        } finally {
            connection.disconnect()
        }
    }
}
