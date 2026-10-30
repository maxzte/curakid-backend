package com.misterio.curakid

import io.gatling.javaapi.core.CoreDsl.*
import io.gatling.javaapi.core.Simulation
import io.gatling.javaapi.http.HttpDsl.*
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

/**
 * Gatling Load Simulation for Curakid Backend.
 *
 * Scenarios:
 * 1. Auth & Onboarding (/api/v1/auth/register, /api/v1/auth/login, /api/v1/children)
 * 2. Growth Tracking & Measurements (/api/v1/children/{childId}/measurements)
 * 3. AI RAG Chat & Real-Time Triage (/api/v1/chat/sessions, /api/v1/chat/triage)
 * 4. Developmental Assessment & Reports (/api/v1/children/{childId}/assessments, /api/v1/children/{childId}/reports)
 */
class CurakidSimulation : Simulation() {

    private val httpProtocol = http
        .baseUrl("http://localhost:8080")
        .acceptHeader("application/json")
        .contentTypeHeader("application/json")

    // --- Feeders ---
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

    // --- Common Auth & Onboarding Chain ---
    private val authChain = exec(
        http("Register User")
            .post("/api/v1/auth/register")
            .body(StringBody("""{"email": "#{email}", "password": "Password123!", "name": "Gatling User"}"""))
            .check(status().`is`(200))
    )
        .exitHereIfFailed()
        .pause(1)
        .exec(
            http("Login User")
                .post("/api/v1/auth/login")
                .body(StringBody("""{"email": "#{email}", "password": "Password123!"}"""))
                .check(status().`is`(200))
                .check(jsonPath("$.accessToken").saveAs("accessToken"))
        )
        .exitHereIfFailed()
        .pause(1)
        .exec(
            http("Get Profile Me")
                .get("/api/v1/auth/me")
                .header("Authorization", "Bearer #{accessToken}")
                .check(status().`is`(200))
        )
        .pause(1)
        .feed(childDataFeeder)
        .exec(
            http("Create Child Profile")
                .post("/api/v1/children")
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

    // --- 1. Auth & Onboarding Scenario ---
    private val authScenario = scenario("User Auth & Onboarding")
        .feed(emailFeeder)
        .exec(authChain)

    // --- 2. Growth Tracking Scenario ---
    private val growthTrackingScenario = scenario("Growth Tracking & Measurements")
        .feed(emailFeeder)
        .exec(authChain)
        .repeat(3).on(
            pause(2)
                .exec { session ->
                    session.set("measurementId", UUID.randomUUID().toString())
                }
                .exec(
                    http("Add Measurement")
                        .post("/api/v1/children/#{childId}/measurements")
                        .header("Authorization", "Bearer #{accessToken}")
                        .body(StringBody { session ->
                            val measurementId = session.getString("measurementId")
                            val weight = ThreadLocalRandom.current().nextDouble(9.0, 10.5)
                            val height = ThreadLocalRandom.current().nextDouble(74.0, 77.0)
                            val headCirc = ThreadLocalRandom.current().nextDouble(44.0, 47.0)
                            val now = Instant.now().toString()

                            """{
                                "id": "$measurementId",
                                "weightKg": $weight,
                                "heightCm": $height,
                                "headCircCm": $headCirc,
                                "measurementDate": "$now"
                            }"""
                        })
                        .check(status().`is`(201))
                )
                .pause(1)
                .exec(
                    http("Get Measurements List")
                        .get("/api/v1/children/#{childId}/measurements")
                        .header("Authorization", "Bearer #{accessToken}")
                        .check(status().`is`(200))
                )
        )

    // --- 3. AI RAG Chat & Triage Scenario ---
    private val chatScenario = scenario("AI RAG Chat & Real-Time Triage")
        .feed(emailFeeder)
        .exec(authChain)
        .pause(1)
        .exec(
            http("Create Chat Session")
                .post("/api/v1/chat/sessions")
                .header("Authorization", "Bearer #{accessToken}")
                .body(StringBody("""{"childId": "#{childId}", "title": "Load Test Chat"}"""))
                .check(status().`is`(200))
                .check(jsonPath("$.id").saveAs("sessionId"))
        )
        .pause(1)
        .exec(
            http("Perform Chat Triage")
                .post("/api/v1/chat/triage")
                .header("Authorization", "Bearer #{accessToken}")
                .body(
                    StringBody(
                        """{
                            "message": "У малюка нежить та незначна температура",
                            "childContext": {"ageMonths": 12, "allergies": []}
                        }"""
                    )
                )
                .check(status().`is`(200))
        )
        .pause(2)
        .exec(
            http("Send Chat Message")
                .post("/api/v1/chat/sessions/#{sessionId}/messages")
                .header("Authorization", "Bearer #{accessToken}")
                .body(StringBody("""{"content": "Які профілактичні заходи доречні при перших симптомах застуди?"}"""))
                .check(status().`is`(200))
        )

    // --- 4. Developmental Assessment Scenario ---
    private val assessmentScenario = scenario("Developmental Assessment & Reports")
        .feed(emailFeeder)
        .exec(authChain)
        .pause(2)
        .exec(
            http("Get Child Assessments")
                .get("/api/v1/children/#{childId}/assessments")
                .header("Authorization", "Bearer #{accessToken}")
                .check(status().`is`(200))
        )
        .pause(1)
        .exec(
            http("Get Child Reports")
                .get("/api/v1/children/#{childId}/reports")
                .header("Authorization", "Bearer #{accessToken}")
                .check(status().`is`(200))
        )

    // --- Load Simulation Setup ---
    init {
        setUp(
            authScenario.injectOpen(atOnceUsers(2), rampUsers(5).during(10)),
            growthTrackingScenario.injectOpen(rampUsers(20).during(20), nothingFor(10), rampUsers(50).during(30)),
            chatScenario.injectOpen(nothingFor(15), rampUsers(15).during(30)),
            assessmentScenario.injectOpen(nothingFor(20), rampUsers(25).during(20))
        ).protocols(httpProtocol)
            .assertions(
                global().responseTime().mean().lt(500),
                global().responseTime().percentile(95.0).lt(1200),
                global().successfulRequests().percent().gt(99.0)
            )
    }

    private fun feeder(f: () -> Map<String, Any?>) = generateSequence(f).iterator()
}
