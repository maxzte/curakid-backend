package com.misterio.curakid.config

import com.misterio.curakid.feature.notification.NotificationTopics
import com.misterio.curakid.feature.reports.ReportTopics
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.*
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer
import org.springframework.kafka.support.serializer.JacksonJsonSerializer
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper

@Configuration
@EnableKafka
class KafkaConfig(
    @Value("\${spring.kafka.bootstrap-servers}")
    private val bootstrapServers: String,
    @Value("\${spring.kafka.consumer.group-id:curakid-main-group}")
    private val consumerGroupId: String
) {

    @Bean
    fun notificationsTopic(): NewTopic {
        return TopicBuilder.name(NotificationTopics.NOTIFICATIONS)
            .partitions(3)
            .replicas(1)
            .build()
    }

    @Bean
    fun notificationsDlqTopic(): NewTopic {
        return TopicBuilder.name(NotificationTopics.NOTIFICATIONS_DLQ)
            .partitions(1)
            .replicas(1)
            .build()
    }

    @Bean
    fun aiReportsTopic(): NewTopic {
        return TopicBuilder.name(ReportTopics.AI_REPORTS)
            .partitions(3)
            .replicas(1)
            .build()
    }

    @Bean
    fun aiReportsDlqTopic(): NewTopic {
        return TopicBuilder.name(ReportTopics.AI_REPORTS_DLQ)
            .partitions(1)
            .replicas(1)
            .build()
    }

    @Bean
    fun producerFactory(): ProducerFactory<String, Any> {
        val configProps = mutableMapOf<String, Any>(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JacksonJsonSerializer::class.java,
            ProducerConfig.ACKS_CONFIG to "all",
            ProducerConfig.RETRIES_CONFIG to 3,
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to true,
            ProducerConfig.RECONNECT_BACKOFF_MS_CONFIG to 1000L,
            ProducerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG to 10000L,
            ProducerConfig.RETRY_BACKOFF_MS_CONFIG to 500L
        )
        return DefaultKafkaProducerFactory(configProps, StringSerializer(), JacksonJsonSerializer())
    }

    @Bean
    fun kafkaTemplate(producerFactory: ProducerFactory<String, Any>): KafkaTemplate<String, Any> {
        return KafkaTemplate(producerFactory)
    }

    @Bean
    fun jsonMapper(): JsonMapper {
        return JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build()
    }

    @Bean
    fun consumerFactory(jsonMapper: JsonMapper): ConsumerFactory<String, Any> {
        val configProps = mutableMapOf<String, Any>(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to consumerGroupId,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to JacksonJsonDeserializer::class.java,
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
            ConsumerConfig.RECONNECT_BACKOFF_MS_CONFIG to 1000L,
            ConsumerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG to 10000L,
            ConsumerConfig.RETRY_BACKOFF_MS_CONFIG to 500L
        )
        val deserializer = JacksonJsonDeserializer(Any::class.java, jsonMapper)
        deserializer.addTrustedPackages("com.misterio.curakid.*")
        return DefaultKafkaConsumerFactory(configProps, StringDeserializer(), deserializer)
    }

    @Bean
    fun kafkaListenerContainerFactory(
        consumerFactory: ConsumerFactory<String, Any>,
        kafkaTemplate: KafkaTemplate<String, Any>
    ): ConcurrentKafkaListenerContainerFactory<String, Any> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, Any>()
        factory.setConsumerFactory(consumerFactory)

        val operations: KafkaOperations<*, *> = kafkaTemplate
        val recoverer = DeadLetterPublishingRecoverer(operations) { record, _ ->
            val dlqTopic = when (record.topic()) {
                NotificationTopics.NOTIFICATIONS -> NotificationTopics.NOTIFICATIONS_DLQ
                ReportTopics.AI_REPORTS -> ReportTopics.AI_REPORTS_DLQ
                else -> "${record.topic()}.dlq"
            }
            TopicPartition(dlqTopic, record.partition().coerceAtMost(0))
        }

        val backOff = ExponentialBackOffWithMaxRetries(3)
        backOff.initialInterval = 1000L
        backOff.multiplier = 2.0
        backOff.maxInterval = 10000L

        val errorHandler = DefaultErrorHandler(recoverer, backOff)
        factory.setCommonErrorHandler(errorHandler)

        return factory
    }
}
