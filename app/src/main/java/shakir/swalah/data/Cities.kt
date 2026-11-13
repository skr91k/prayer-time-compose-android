package shakir.swalah.data

import android.content.Context

/** The bundled world-cities list (name, latitude, longitude) used by the location picker. */
object Cities {
    @Volatile
    private var cache: List<AppLocation>? = null

    fun all(context: Context): List<AppLocation> = cache ?: synchronized(this) {
        cache ?: context.assets.open("worldcities.csv").bufferedReader().useLines { lines ->
            lines.mapNotNull { line ->
                val p = line.trim().split(',')
                if (p.size < 3) null
                else runCatching { AppLocation(p[0], p[1].toDouble(), p[2].toDouble()) }.getOrNull()
            }.toList()
        }.also { cache = it }
    }
}
