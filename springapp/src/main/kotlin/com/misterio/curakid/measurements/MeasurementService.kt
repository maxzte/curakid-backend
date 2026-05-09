package com.misterio.curakid.measurements

import com.misterio.curakid.children.Child
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class MeasurementService(
    private val measurementRepository: MeasurementRepository,
    private val whoZScoreCalculator: WhoZScoreCalculator
) {
    fun getMeasurements(child: Child): List<MeasurementResponse> {
        return measurementRepository.findAllByChildOrderByMeasurementDateAsc(child)
            .map { MeasurementResponse.from(it) }
    }

    @Transactional
    fun addMeasurement(child: Child, request: AddMeasurementRequest): MeasurementResponse {
        val measurement = Measurement(
            child = child,
            weightKg = request.weightKg,
            heightCm = request.heightCm,
            headCircCm = request.headCircCm,
            measurementDate = request.measurementDate
        )

        // Calculate WHO Z-scores if enough data is provided
        if (request.weightKg != null) {
            measurement.wfaZscore = whoZScoreCalculator.calculateWfaZscore(
                request.weightKg, child.birthDate, child.gender
            )
        }
        if (request.heightCm != null) {
            measurement.hfaZscore = whoZScoreCalculator.calculateHfaZscore(
                request.heightCm, child.birthDate, child.gender
            )
        }
        if (request.weightKg != null && request.heightCm != null) {
            measurement.bmiZscore = whoZScoreCalculator.calculateBmiZscore(
                request.weightKg, request.heightCm, child.birthDate, child.gender
            )
        }

        return MeasurementResponse.from(measurementRepository.save(measurement))
    }
}
