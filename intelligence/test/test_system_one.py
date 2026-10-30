from src.main.system_one import LayaModelDriver, SystemOneEngine, HeuristicDecisionEngine


def test_triage_chat_red_flags(client, auth_headers):
    """Перевіряє виявлення термінових червоних прапорців у чаті."""
    # Given
    payload = {
        "message": "Дитина важко дихає, сині губи і судоми!",
        "childContext": {"ageMonths": 8, "allergies": []}
    }

    # When
    response = client.post("/triage-chat", headers=auth_headers, json=payload)

    # Then
    assert response.status_code == 200
    res = response.json()
    assert res["urgencyLevel"] == "URGENT_RED_FLAG"
    assert res["intent"] == "EMERGENCY_RED_FLAG"
    assert res["needsRagSearch"] is True


def test_triage_chat_medication_query(client, auth_headers):
    """Перевіряє виявлення запиту щодо дозування або ліків."""
    # Given
    payload = {
        "message": "Яке дозування нурофену давати дитині при температурі?",
        "childContext": {"ageMonths": 14, "allergies": []}
    }

    # When
    response = client.post("/triage-chat", headers=auth_headers, json=payload)

    # Then
    assert response.status_code == 200
    res = response.json()
    assert res["asksForMedicationOrDiagnosis"] is True
    assert res["needsRagSearch"] is True


def test_triage_chat_greeting(client, auth_headers):
    """Перевіряє класифікацію привітань та загальних фраз без RAG."""
    # Given
    payload = {
        "message": "Привіт! Підкажи, як ти можеш мені допомогти?",
        "childContext": {"ageMonths": 6, "allergies": []}
    }

    # When
    response = client.post("/triage-chat", headers=auth_headers, json=payload)

    # Then
    assert response.status_code == 200
    res = response.json()
    assert res["intent"] == "GENERAL_GREETING_OR_OFFTOPIC"
    assert res["needsRagSearch"] is False


def test_triage_chat_categories_coverage(client, auth_headers):
    """Перевіряє категорітизацію різних інтентів повідомлень."""
    # Given / When
    resp1 = client.post("/triage-chat", headers=auth_headers, json={
        "message": "Коли вводити прикорм та грудне молочко?",
        "childContext": {"ageMonths": 6, "allergies": []}
    }).json()

    resp2 = client.post("/triage-chat", headers=auth_headers, json={
        "message": "Коли дитина починає повзати та говорить перші слова?",
        "childContext": {"ageMonths": 10, "allergies": []}
    }).json()

    resp3 = client.post("/triage-chat", headers=auth_headers, json={
        "message": "Яка нормальна вага та зріст у 1 рік за центильними таблицями?",
        "childContext": {"ageMonths": 12, "allergies": []}
    }).json()

    resp4 = client.post("/triage-chat", headers=auth_headers, json={
        "message": "У малюка нежить, блювота і висока температура 38",
        "childContext": {"ageMonths": 18, "allergies": []}
    }).json()

    # Then
    assert resp1["intent"] == "NUTRITION_FEEDING"
    assert resp2["intent"] == "DEVELOPMENT_MILESTONES"
    assert resp3["intent"] == "ANTHROPOMETRY_GROWTH"
    assert resp4["intent"] == "SYMPTOM_OR_ILLNESS"


def test_evaluate_report_guardrail_valid(client, auth_headers):
    """Перевіряє успішну валідацію безпечного медичного звіту."""
    # Given
    payload = {
        "childContext": {"ageMonths": 12, "allergies": []},
        "chunks": [{"title": "Норми сну", "content": "Дитина має спати 12-14 годин."}],
        "reportText": "Сон дитини відповідає віковим рекомендаціям.",
        "recommendations": "Дотримуйтесь стабільного режиму сну та зверніться до педіатра при змінах."
    }

    # When
    response = client.post("/evaluate", headers=auth_headers, json=payload)

    # Then
    assert response.status_code == 200
    res = response.json()
    assert res["passedGuardrail"] is True


def test_evaluate_report_guardrail_medication_violation(client, auth_headers):
    """Перевіряє блокування звіту із спробою встановлення діагнозу чи призначення ліків."""
    # Given
    payload = {
        "childContext": {"ageMonths": 12, "allergies": []},
        "chunks": [],
        "reportText": "У дитини діагноз: хронічне безсоння.",
        "recommendations": "Призначити приймати антибіотик по 1 таблетці щодня."
    }

    # When
    response = client.post("/evaluate", headers=auth_headers, json=payload)

    # Then
    assert response.status_code == 200
    res = response.json()
    assert res["passedGuardrail"] is False


def test_evaluate_report_guardrail_allergy_violation(client, auth_headers):
    """Перевіряє блокування звіту, що радить алерген."""
    # Given
    payload = {
        "childContext": {"ageMonths": 12, "allergies": ["молоко"]},
        "chunks": [],
        "reportText": "Дитина добре росте.",
        "recommendations": "Щодня введіть та давайте дитині коров'яче молоко."
    }

    # When
    response = client.post("/evaluate", headers=auth_headers, json=payload)

    # Then
    assert response.status_code == 200
    res = response.json()
    assert res["passedGuardrail"] is False


def test_laya_model_driver_and_system_one_mocked(monkeypatch):
    """Тестує SystemOneEngine та LayaModelDriver із змокованим агентом."""
    # Given
    monkeypatch.setenv("FAST_CHAT_TRIAGE", "false")
    driver = LayaModelDriver()

    mock_agent = type("MockAgent", (), {
        "predict": lambda self, state, questions: {
            "requires_doctor_consult": {"prob": 0.85},
            "allergy_or_chronic_constraint": {"prob": 0.10},
            "measurement_outlier_warning": {"prob": 0.15},
            "primary_focus": "REGRESSION_ALERT",
            "anamnesis_severity": {"score": 2.4}
        }
    })()
    driver._agent = mock_agent

    # When
    engine = SystemOneEngine(driver=driver)

    # Then
    assert engine.is_ready is True
    triage_res = engine.decide_clinical_triage({"childId": "c1"})
    assert triage_res["requiresDoctorConsultProbability"] == 0.85
    assert triage_res["primaryFocus"] == "REGRESSION_ALERT"
    assert triage_res["isSystemOne"] is True

    # Test rerank with mocked agent
    driver._agent = type("MockAgent", (), {
        "predict": lambda self, state, questions: {
            "relevance": {"score": 1.8},
            "is_safe_for_child": {"prob": 0.95}
        }
    })()
    reranked = engine.rerank_and_filter_chunks("запитання", {"allergies": []}, [{"title": "T1", "content": "C1"}])
    assert len(reranked) == 1
    assert reranked[0]["relevanceScore"] == 0.90

    # Test evaluate_medical_report with mocked agent
    driver._agent = type("MockAgent", (), {
        "predict": lambda self, state, questions: {
            "faithfulness": {"score": 2.7},
            "relevance": {"score": 2.8},
            "no_diagnosis_or_prescription": {"prob": 0.95},
            "respects_allergies": {"prob": 0.95}
        }
    })()
    eval_res = engine.evaluate_medical_report({}, [], "Звіт", "Поради")
    assert eval_res["faithfulnessScore"] == 0.90
    assert eval_res["passedGuardrail"] is True

    # Test triage_chat_message with mocked agent
    driver._agent = type("MockAgent", (), {
        "predict": lambda self, state, questions: {
            "intent": "SYMPTOM_OR_ILLNESS",
            "urgency_level": "URGENT_RED_FLAG",
            "needs_rag_search": {"prob": 0.95},
            "asks_medication": {"prob": 0.05}
        }
    })()
    chat_res = engine.triage_chat_message("сильні судоми", {})
    assert chat_res["intent"] == "SYMPTOM_OR_ILLNESS"
    assert chat_res["urgencyLevel"] == "URGENT_RED_FLAG"
