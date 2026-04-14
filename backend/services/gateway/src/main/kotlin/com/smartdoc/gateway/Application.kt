package com.smartdoc.gateway

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@SpringBootApplication
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}

@RestController
class RootController {
    @GetMapping("/")
    fun root(): Map<String, String> = mapOf(
        "service" to "gateway",
        "message" to "gateway up"
    )
}

@RestController
@RequestMapping("/api/v1")
class HealthController {
    @GetMapping("/health")
    fun health(): Map<String, String> = mapOf(
        "service" to "gateway",
        "status" to "ok"
    )
}
