package com.misterio.curakid.admin.vaccines

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration

@Component
class VaccineCalendarClient(
    @Value("\${admin.vaccine-calendar.url:https://vaccine.org.ua/vakczynacziya-v-ukrayini/vaccinationua-calendar/}")
    val calendarUrl: String
) {
    private val restClient: RestClient by lazy {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(15))
            setReadTimeout(Duration.ofSeconds(20))
        }
        RestClient.builder()
            .requestFactory(requestFactory)
            .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .defaultHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .defaultHeader("Accept-Language", "uk,en-US;q=0.9,en;q=0.8")
            .build()
    }

    fun fetchCalendarHtml(): String {
        return restClient.get()
            .uri(calendarUrl)
            .retrieve()
            .body(String::class.java)
            ?: throw IllegalStateException("Empty response received from vaccine calendar source: $calendarUrl")
    }
}
