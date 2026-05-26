package com.misterio.curakid.core.database.measurements

import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.BaseEntity
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

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

    @Column(name = "hcfa_zscore", precision = 5, scale = 2)
    var hcfaZscore: BigDecimal? = null, // hcfa-for-age

    @Column(name = "wfa_delta_zscore", precision = 5, scale = 2)
    var wfaDeltaZ: BigDecimal? = null,

    @Column(name = "hfa_delta_zscore", precision = 5, scale = 2)
    var hfaDeltaZ: BigDecimal? = null,

    @Column(name = "bmi_delta_zscore", precision = 5, scale = 2)
    var bmiDeltaZ: BigDecimal? = null,

    @Column(name = "hcfa_delta_zscore", precision = 5, scale = 2)
    var hcfaDeltaZ: BigDecimal? = null,

    @Column(name = "wfh_zscore", precision = 5, scale = 2)
    var wfhZscore: BigDecimal? = null,

    @Column(name = "wfh_delta_zscore", precision = 5, scale = 2)
    var wfhDeltaZ: BigDecimal? = null,

    @Column(name = "wfa_percentile", precision = 5, scale = 2)
    var wfaPercentile: BigDecimal? = null,

    @Column(name = "hfa_percentile", precision = 5, scale = 2)
    var hfaPercentile: BigDecimal? = null,

    @Column(name = "bmi_percentile", precision = 5, scale = 2)
    var bmiPercentile: BigDecimal? = null,

    @Column(name = "hcfa_percentile", precision = 5, scale = 2)
    var hcfaPercentile: BigDecimal? = null,

    @Column(name = "wfh_percentile", precision = 5, scale = 2)
    var wfhPercentile: BigDecimal? = null,

    @Column(name = "measurement_date", nullable = false)
    var measurementDate: Instant = Instant.now()

) : BaseEntity()
