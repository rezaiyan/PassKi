package com.alirezaiyan.passki

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform