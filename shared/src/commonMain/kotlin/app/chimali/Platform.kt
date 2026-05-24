package app.chimali

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
