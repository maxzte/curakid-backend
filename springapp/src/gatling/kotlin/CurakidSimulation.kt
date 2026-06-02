package com.misterio.curakid

import io.gatling.javaapi.core.CoreDsl.StringBody
import io.gatling.javaapi.core.CoreDsl.atOnceUsers
import io.gatling.javaapi.core.CoreDsl.exec
import io.gatling.javaapi.core.CoreDsl.global
import io.gatling.javaapi.core.CoreDsl.holdFor
import io.gatling.javaapi.core.CoreDsl.jsonPath
import io.gatling.javaapi.core.CoreDsl.nothingFor
import io.gatling.javaapi.core.CoreDsl.pause
import io.gatling.javaapi.core.CoreDsl.rampUsers
import io.gatling.javaapi.core.CoreDsl.reachRps
import io.gatling.javaapi.core.CoreDsl.scenario
import io.gatling.javaapi.core.Simulation
import io.gatling.javaapi.http.HttpDsl.http
import io.gatling.javaapi.http.HttpDsl.status
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import kotlin.time.Duration.Companion.seconds

/**
 * Gatling Simulation for Curakid Backend.
 * 1. Auth & Onboarding
 * 2. Growth Tracking (Anthropometry -> Emergency Analysis)
 * 3. Developmental Assessment (Quizzes -> Period Completion)
 * 4. AI Report Generation (RAG -> LLM)
 */
class CurakidSimulation : Simulation() {

    private val httpProtocol = http
        .baseUrl("http://localhost:8080")
        .acceptHeader("application/json")
        .contentTypeHeader("application/json")

    // --- Feeders ---
    // Generate unique emails for each virtual user
    private val emailFeeder = feeder {
        mapOf("email" to "test-user-${UUID.randomUUID()}@gatling.local")
    }

    private val childDataFeeder = feeder {
        mapOf(
            "childName" to "Child-${ThreadLocalRandom.current().nextInt(10000)}",
            "birthDate" to "2023-01-01",
            "gender" to if (ThreadLocalRandom.current().nextBoolean()) "MALE" else "FEMALE"
        )
    }

    // --- Common Fragments (ChainBuilder) ---
    private val authChain = exec(
        http("Register")
            .post("/api/auth/register")
            .body(StringBody("""{"email": "#{email}", "password": "Password123!", "name": "Gatling User"}"""))
            .check(status().`is`(200))
    )
        .exitHereIfFailed()
        .pause(1)
        .exec(
            http("Login")
                .post("/api/auth/login")
                .body(StringBody("""{"email": "#{email}", "password": "Password123!"}"""))
                .check(status().`is`(200))
                .check(jsonPath("$.accessToken").saveAs("accessToken"))
        )
        .exitHereIfFailed()
        .pause(2)
        .feed(childDataFeeder)
        .exec(
            http("Create Child")
                .post("/api/children")
                .header("Authorization", "Bearer #{accessToken}")
                .body(
                    StringBody(
                        """{
                    "name": "#{childName}",
                    "birthDate": "#{birthDate}",
                    "gender": "#{gender}",
                    "isPremature": false
                }"""
                    )
                )
                .check(status().`is`(201))
                .check(jsonPath("$.id").saveAs("childId"))
        )
        .exitHereIfFailed()

    // --- Scenarios ---
    private val authScenario = scenario("User Auth & Onboarding")
        .feed(emailFeeder)
        .exec(authChain)

    private val growthTrackingScenario = scenario("Growth Tracking (Normal Measures)")
        .feed(emailFeeder)
        .exec(authChain)
        .repeat(3).on(
            pause(2)
                .exec(
                    http("Add Measurement")
                        .post("/api/children/#{childId}/measurements")
                        .header("Authorization", "Bearer #{accessToken}")
                        .body(StringBody { session ->
                            val weight = ThreadLocalRandom.current().nextDouble(9.0, 10.5)
                            val height = ThreadLocalRandom.current().nextDouble(74.0, 77.0)
                            val today = Instant.now().toString()

                            """{
                                "weightKg": $weight,
                                "heightCm": $height,
                                "measurementDate": "$today"
                            }"""
                        })
                        .check(status().`is`(201))
                        .check(jsonPath("$.aiReportId").optional().saveAs("emergencyReportId"))
                )
//            .doIf { session -> session.contains("emergencyReportId") }.then(
//                exec(
//                    http("Get Emergency Report")
//                        .get("/api/reports/#{emergencyReportId}")
//                        .header("Authorization", "Bearer #{accessToken}")
//                        .check(status().`is`(200))
//                )
//            )
        )

    private val assessmentScenario = scenario("Developmental Assessment Flow")
        .feed(emailFeeder)
        .exec(authChain)
        .pause(2)
        .exec(
            http("Get Assessments")
                .get("/api/children/#{childId}/assessments")
                .header("Authorization", "Bearer #{accessToken}")
                .check(status().`is`(200))
        )
        .pause(1)
//        .exec(
//            http("Complete Period")
//                .post("/api/children/#{childId}/assessments/complete")
//                .header("Authorization", "Bearer #{accessToken}")
//                .body(StringBody("""{"periodCode": "M6", "allowEarlyTransition": true}"""))
//                .check(status().`in`(200, 202))
//                .check(jsonPath("$.reportId").optional().saveAs("finalReportId"))
//        )

    // --- Load Simulation Setup ---
    init {
        setUp(
            authScenario.injectOpen(atOnceUsers(2), rampUsers(5).during(10)),
            growthTrackingScenario.injectOpen(rampUsers(30).during(30), nothingFor(15), rampUsers(100).during(60)),
            assessmentScenario.injectOpen(nothingFor(30), rampUsers(50).during(30))
        ).protocols(httpProtocol)
            .assertions(
                global().responseTime().mean().lt(800),
                global().responseTime().percentile(95.0).lt(1500),
                global().successfulRequests().percent().gt(99.0)
            )
//        setUp(
//            authScenario.injectOpen(atOnceUsers(5), rampUsers(10).during(15)),
//            growthTrackingScenario.injectOpen(rampUsers(30).during(30)),
//            assessmentScenario.injectOpen(rampUsers(20).during(20))
//        )
//            .throttle(
//                reachRps(150).during(30L),
//                holdFor(120L)
//            )
//            .maxDuration(Duration.ofMinutes(5))
//            .protocols(httpProtocol)
    }

    // Helper to create a feeder from a generator function
    private fun feeder(f: () -> Map<String, Any?>) = generateSequence(f).iterator()
}
