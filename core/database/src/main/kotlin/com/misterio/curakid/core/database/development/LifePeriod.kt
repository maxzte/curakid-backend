package com.misterio.curakid.core.database.development

import com.misterio.curakid.core.database.BaseEntity
import com.misterio.curakid.core.database.question.Question
import jakarta.persistence.*
import org.hibernate.annotations.JdbcType
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.sql.JDBCType

// https://zakon.rada.gov.ua/laws/show/z1936-24#n72
@Entity
@Table(name = "life_periods")
class LifePeriod(

    @Column(nullable = false, unique = true)
    var code: String = "",

    @Column(nullable = false)
    var label: String = "",

    /**
     * {
     *     "ua":{
     *         "title":"Час чомучок та перших сюжетних ігор",
     *         "subtitle":"Період активної соціалізації, швидкого розвитку мовлення та фантазії.",
     *         "milestones":{
     *             "movement":"легко тримає рівновагу, стрибає на двох ногах, освоює триколісний велосипед чи біговел.",
     *             "speech":"розмовляє реченнями, ставить перші запитання «чому?» та «де?».",
     *             "cognition":"грає у розгорнуті сюжетні ігри (лікує ведмедика, будує гараж для машинок)."
     *         },
     *         "tips": [
     *              "вчіть малюка розпізнавати емоції. Проговорюйте разом: «Ти зараз радієш?», «Тобі сумно?», допомагаючи зрозуміти себе.",
     *         ]
     *     }
     * }
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    var description: String = "",

    @Column(name = "min_months", nullable = false)
    var minMonths: Int = 0,

    @Column(name = "max_months", nullable = false)
    var maxMonths: Int = 0,

    @OneToMany(mappedBy = "lifePeriod", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    @OrderBy("weight ASC")
    val questions: MutableList<Question> = mutableListOf()

) : BaseEntity()
