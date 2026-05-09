package com.misterio.curakid.measurements

import com.misterio.curakid.children.Child
import com.misterio.curakid.common.BaseEntity
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "measurements")
class Measurement(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_id", nullable = false)
    var child: Child? = null,

    @Column(name = "weight_kg", precision = 5, scale = 2)
    var weightKg: BigDecimal? = null,

    @Column(name = "height_cm", precision = 5, scale = 2)
    var heightCm: BigDecimal? = null,

    @Column(name = "head_circ_cm", precision = 5, scale = 2)
    var headCircCm: BigDecimal? = null,

    // WHO Z-Scores
    @Column(name = "wfa_zscore", precision = 5, scale = 2)
    var wfaZscore: BigDecimal? = null, // Weight-for-age

    @Column(name = "hfa_zscore", precision = 5, scale = 2)
    var hfaZscore: BigDecimal? = null, // Height-for-age

    @Column(name = "bmi_zscore", precision = 5, scale = 2)
    var bmiZscore: BigDecimal? = null, // BMI-for-age

    @Column(name = "measurement_date", nullable = false)
    var measurementDate: LocalDateTime = LocalDateTime.now()

) : BaseEntity()
