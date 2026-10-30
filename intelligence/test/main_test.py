import os
import pytest

# Ensure tests run in offline mode without attempting network downloads for HuggingFace models
os.environ["HF_HUB_OFFLINE"] = "1"
os.environ["TRANSFORMERS_OFFLINE"] = "1"

from fastapi.testclient import TestClient

from src.main.main import app

client = TestClient(app)
API_KEY = "test-secret-key"


@pytest.fixture(autouse=True)
def setup_env(monkeypatch):
    """Автоматично виставляє тестовий API-ключ для кожного тесту"""
    monkeypatch.setenv("INTERNAL_API_KEY", API_KEY)
    monkeypatch.setenv("HF_HUB_OFFLINE", "1")
    monkeypatch.setenv("TRANSFORMERS_OFFLINE", "1")
    import src.main.main
    monkeypatch.setattr(src.main.main, "INTERNAL_API_KEY", API_KEY)


# --- ТЕСТИ БЕЗПЕКИ ТА HEALTH ---

def test_health_endpoint():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_secured_endpoint_without_header():
    valid_payload = {"childId": "test-child", "ageMonths": 12, "rules": []}
    response = client.post("/analyze", json=valid_payload)
    assert response.status_code == 422


def test_secured_endpoint_with_invalid_header():
    valid_payload = {
        "childId": "test-child",
        "ageMonths": 12,
        "rules": []
    }
    headers = {"X-Internal-Key": "wrong-key"}
    response = client.post("/analyze", headers=headers, json=valid_payload)
    assert response.status_code == 403
    assert response.json()["detail"] == "Forbidden"


# --- ТЕСТИ ЕНДПОЇНТУ /analyze (Fuzzy Inference System) ---

def test_analyze_with_valid_rules_and_data():
    headers = {"X-Internal-Key": API_KEY}

    payload = {
        "childId": "child-123",
        "ageMonths": 14,
        "gender": "MALE",
        "wfaZscore": -1.5,
        "speechScore": 45.0,
        "rules": [
            {
                "conditions": [
                    {"variable": "wfaZscore", "term": "LOW"},
                    {"variable": "speechScore", "term": "BELOW_NORMAL"}
                ],
                "consequent": "MEDIUM"
            }
        ],
        "norms": {
            "wfaZscore": {"P3": -3.0, "P15": -2.0, "P50": 0.0, "P85": 2.0, "P97": 3.0}
        }
    }

    response = client.post("/analyze", headers=headers, json=payload)

    assert response.status_code == 200
    json_data = response.json()

    assert "urgencyScore" in json_data
    assert "isFallback" in json_data
    assert "breakdown" in json_data
    assert "linguisticInterpretations" in json_data
    assert json_data["isFallback"] is False


def test_analyze_fallback_trigger():
    headers = {"X-Internal-Key": API_KEY}

    payload = {
        "childId": "child-789",
        "ageMonths": 6,
        "motorGrossScore": 50.0,
        "rules": []
    }

    response = client.post("/analyze", headers=headers, json=payload)

    assert response.status_code == 200
    json_data = response.json()
    assert json_data["isFallback"] is True
    assert "motor_gross" in json_data["breakdown"]


def test_analyze_with_unused_inputs():
    headers = {"X-Internal-Key": API_KEY}

    payload = {
        "childId": "child-456",
        "ageMonths": 14,
        "gender": "MALE",
        "wfaZscore": -1.5,
        "hcfaZscore": 0.5, # hcfaZscore is provided but NOT used in the rule
        "rules": [
            {
                "conditions": [
                    {"variable": "wfaZscore", "term": "LOW"}
                ],
                "consequent": "MEDIUM"
            }
        ],
        "norms": {
            "wfaZscore": {"P3": -3.0, "P15": -2.0, "P50": 0.0, "P85": 2.0, "P97": 3.0}
        }
    }

    response = client.post("/analyze", headers=headers, json=payload)

    assert response.status_code == 200
    json_data = response.json()
    assert json_data["isFallback"] is False
    assert "urgencyScore" in json_data

def test_analyze_with_trends_and_regression():
    headers = {"X-Internal-Key": API_KEY}

    payload = {
        "childId": "child-trend",
        "ageMonths": 14,
        "wfaZscore": -2.1,
        "wfaVelocity": -0.3,
        "regressions": {"MOTOR_GROSS": True},
        "rules": [
            {
                "conditions": [
                    {"variable": "wfaZscore", "term": "LOW"},
                    {"variable": "wfaVelocity", "term": "FALLING"},
                    {"variable": "is_regression", "term": "TRUE"}
                ],
                "consequent": "HIGH"
            }
        ]
    }

    response = client.post("/analyze", headers=headers, json=payload)

    assert response.status_code == 200
    json_data = response.json()
    assert json_data["isFallback"] is False
    assert json_data["urgencyScore"] >= 0.7  # Should be high
    assert "Динаміка ваги: Уповільнення" in json_data["linguisticInterpretations"]
    assert "Регрес розвитку: Виявлено" in json_data["linguisticInterpretations"]

def test_analyze_age_specific_priority():
    headers = {"X-Internal-Key": API_KEY}

    # Common Rules
    rules = [
        # For infants, VERY_LOW hcfa is critical
        {
            "conditions": [
                {"variable": "ageMonths", "term": "INFANT"},
                {"variable": "hcfaZscore", "term": "VERY_LOW"}
            ],
            "consequent": "HIGH"
        },
        # For preschoolers, VERY_LOW hcfa is medium (less critical)
        {
            "conditions": [
                {"variable": "ageMonths", "term": "PRESCHOOL"},
                {"variable": "hcfaZscore", "term": "VERY_LOW"}
            ],
            "consequent": "MEDIUM"
        }
    ]

    # Scenario 1: Infant (3 weeks / 0 months) with hcfa -3.8
    payload_infant = {
        "childId": "infant",
        "ageMonths": 0,
        "hcfaZscore": -3.8,
        "rules": rules
    }
    response_infant = client.post("/analyze", headers=headers, json=payload_infant)
    score_infant = response_infant.json()["urgencyScore"]

    # Scenario 2: Preschooler (60 months) with same hcfa -3.8
    payload_pre = {
        "childId": "preschooler",
        "ageMonths": 60,
        "hcfaZscore": -3.8,
        "rules": rules
    }
    response_pre = client.post("/analyze", headers=headers, json=payload_pre)
    score_pre = response_pre.json()["urgencyScore"]

    assert score_infant > 0.75
    assert score_pre < 0.7
    assert score_infant > score_pre


# --- ТЕСТИ SYSTEM 1 (Laya / Jev-підхід) ---

def test_analyze_with_system_one_triage():
    headers = {"X-Internal-Key": API_KEY}
    payload = {
        "childId": "child-s1",
        "ageMonths": 18,
        "wfaZscore": -2.5,
        "allergies": ["молоко", "арахіс"],
        "chronicConditions": [],
        "notes": ["малюк млявий, погано їсть"],
        "regressions": {"SPEECH": True},
        "rules": []
    }
    response = client.post("/analyze", headers=headers, json=payload)
    assert response.status_code == 200
    data = response.json()
    assert "systemOneDecisions" in data
    decisions = data["systemOneDecisions"]
    assert decisions["requiresDoctorConsultProbability"] >= 0.5
    assert decisions["allergyOrChronicConstraintProbability"] >= 0.5
    assert decisions["primaryFocus"] in ["REGRESSION_ALERT", "ANTHROPOMETRY_NUTRITION"]
    assert data["urgencyScore"] >= 0.5


def test_rerank_endpoint():
    headers = {"X-Internal-Key": API_KEY}
    payload = {
        "query": "харчування дитини 1 рік",
        "childContext": {
            "ageMonths": 12,
            "allergies": ["молоко"]
        },
        "chunks": [
            {
                "title": "Грудне та альтернативне вигодовування",
                "content": "Загальні рекомендації щодо раціону дитини в 1 рік."
            },
            {
                "title": "Молочні продукти для малюків",
                "content": "Потрібно вживати незбиране молоко щодня."
            }
        ]
    }
    response = client.post("/rerank", headers=headers, json=payload)
    assert response.status_code == 200
    reranked = response.json()["chunks"]
    # Unsafe chunk with allergen should be filtered out or deprioritized
    assert len(reranked) >= 1
    safe_titles = [c["title"] for c in reranked]
    assert "Грудне та альтернативне вигодовування" in safe_titles


def test_evaluate_endpoint_guardrail():
    headers = {"X-Internal-Key": API_KEY}
    
    # Valid report test
    valid_payload = {
        "childContext": {"ageMonths": 12, "allergies": []},
        "chunks": [{"title": "Норми сну", "content": "Дитина має спати 12-14 годин."}],
        "reportText": "Сон дитини відповідає віковим рекомендаціям.",
        "recommendations": "Дотримуйтесь стабільного режиму сну та зверніться до педіатра при змінах."
    }
    response = client.post("/evaluate", headers=headers, json=valid_payload)
    assert response.status_code == 200
    res = response.json()
    assert res["passedGuardrail"] is True
    assert res["faithfulnessScore"] >= 0.75

    # Violating report (prescribing medication / diagnosis)
    invalid_payload = {
        "childContext": {"ageMonths": 12, "allergies": []},
        "chunks": [{"title": "Норми сну", "content": "Дитина має спати 12-14 годин."}],
        "reportText": "У дитини діагноз: хронічне безсоння.",
        "recommendations": "Призначити приймати антибіотик по 1 таблетці щодня."
    }
    response_invalid = client.post("/evaluate", headers=headers, json=invalid_payload)
    assert response_invalid.status_code == 200
    res_invalid = response_invalid.json()
    assert res_invalid["passedGuardrail"] is False
    assert len(res_invalid["violationReasons"]) > 0


def test_triage_chat_endpoint_red_flag():
    headers = {"X-Internal-Key": API_KEY}
    payload = {
        "message": "Дитина важко дихає, сині губи і судоми!",
        "childContext": {"ageMonths": 8, "allergies": []}
    }
    response = client.post("/triage-chat", headers=headers, json=payload)
    assert response.status_code == 200
    res = response.json()
    assert res["urgencyLevel"] == "URGENT_RED_FLAG"
    assert res["intent"] == "EMERGENCY_RED_FLAG"
    assert res["needsRagSearch"] is True
    assert len(res["suggestedPrompts"]) > 0


def test_triage_chat_endpoint_medication_query():
    headers = {"X-Internal-Key": API_KEY}
    payload = {
        "message": "Яке дозування нурофену давати дитині при температурі?",
        "childContext": {"ageMonths": 14, "allergies": []}
    }
    response = client.post("/triage-chat", headers=headers, json=payload)
    assert response.status_code == 200
    res = response.json()
    assert res["asksForMedicationOrDiagnosis"] is True
    assert res["needsRagSearch"] is True


def test_triage_chat_endpoint_greeting():
    headers = {"X-Internal-Key": API_KEY}
    payload = {
        "message": "Привіт! Підкажи, як ти можеш мені допомогти?",
        "childContext": {"ageMonths": 6, "allergies": []}
    }
    response = client.post("/triage-chat", headers=headers, json=payload)
    assert response.status_code == 200
    res = response.json()
    assert res["intent"] == "GENERAL_GREETING_OR_OFFTOPIC"
    assert res["needsRagSearch"] is False
    assert res["urgencyLevel"] == "ROUTINE"


# --- ТЕСТИ КАТЕГОРІЙ TriageChat (Евристичні гілки) ---

def test_triage_chat_categories():
    headers = {"X-Internal-Key": API_KEY}

    # 1. Харчування та прикорм
    resp1 = client.post("/triage-chat", headers=headers, json={
        "message": "Коли вводити прикорм та грудне молочко?",
        "childContext": {"ageMonths": 6, "allergies": []}
    }).json()
    assert resp1["intent"] == "NUTRITION_FEEDING"
    assert resp1["needsRagSearch"] is True

    # 2. Моторний та мовленнєвий розвиток
    resp2 = client.post("/triage-chat", headers=headers, json={
        "message": "Коли дитина починає повзати та говорити перші слова?",
        "childContext": {"ageMonths": 10, "allergies": []}
    }).json()
    assert resp2["intent"] == "DEVELOPMENT_MILESTONES"
    assert resp2["needsRagSearch"] is True

    # 3. Антропометрія та зріст/вага
    resp3 = client.post("/triage-chat", headers=headers, json={
        "message": "Яка нормальна вага та зріст у 1 рік за центильними таблицями?",
        "childContext": {"ageMonths": 12, "allergies": []}
    }).json()
    assert resp3["intent"] == "ANTHROPOMETRY_GROWTH"
    assert resp3["needsRagSearch"] is True

    # 4. Симптоми та застуди
    resp4 = client.post("/triage-chat", headers=headers, json={
        "message": "У малюка нежить, блювота і висока температура 38",
        "childContext": {"ageMonths": 18, "allergies": []}
    }).json()
    assert resp4["intent"] == "SYMPTOM_OR_ILLNESS"
    assert resp4["urgencyLevel"] == "CONSULT_PEDIATRICIAN"

    # 5. Загальне запитання без ключових слів
    resp5 = client.post("/triage-chat", headers=headers, json={
        "message": "Розкажи про найкращі іграшки для розвитку дитини",
        "childContext": {"ageMonths": 24, "allergies": []}
    }).json()
    assert resp5["intent"] == "SYMPTOM_OR_ILLNESS"
    assert resp5["urgencyLevel"] == "ROUTINE"


def test_evaluate_endpoint_allergy_guardrail_violation():
    headers = {"X-Internal-Key": API_KEY}
    payload = {
        "childContext": {"ageMonths": 12, "allergies": ["молоко"]},
        "chunks": [{"title": "Харчування", "content": "Загальні норми раціону."}],
        "reportText": "Дитина добре росте та має чудовий апетит.",
        "recommendations": "Щодня введіть та давайте дитині коров'яче молоко."
    }
    response = client.post("/evaluate", headers=headers, json=payload)
    assert response.status_code == 200
    res = response.json()
    assert res["passedGuardrail"] is False
    assert any("алергіями" in v for v in res["violationReasons"])


# --- ТЕСТИ ПОМИЛОК ТА FALLBACK У ЕНДПОЇНТАХ ---

def test_analyze_endpoint_exception_handling():
    headers = {"X-Internal-Key": API_KEY}
    from src.main.main import app, get_fuzzy_engine, FuzzyEngine

    class FailingFuzzyEngine(FuzzyEngine):
        def compute_fuzzy_inference(self, request_data):
            raise RuntimeError("Simulated Fuzzy Engine Failure")

    app.dependency_overrides[get_fuzzy_engine] = lambda: FailingFuzzyEngine()
    try:
        payload = {"childId": "err-child", "ageMonths": 12, "rules": []}
        response = client.post("/analyze", headers=headers, json=payload)
        assert response.status_code == 200
        res = response.json()
        assert res["isFallback"] is True
    finally:
        app.dependency_overrides.clear()


def test_rerank_endpoint_exception_handling():
    headers = {"X-Internal-Key": API_KEY}
    from src.main.main import app, get_decision_engine, SystemOneEngine

    class FailingDecisionEngine(SystemOneEngine):
        def rerank_and_filter_chunks(self, *args, **kwargs):
            raise RuntimeError("Simulated Rerank Engine Failure")

    app.dependency_overrides[get_decision_engine] = lambda: FailingDecisionEngine()
    try:
        payload = {
            "query": "тест",
            "childContext": {"ageMonths": 12},
            "chunks": [{"title": "T1", "content": "C1"}]
        }
        response = client.post("/rerank", headers=headers, json=payload)
        assert response.status_code == 200
        res = response.json()
        assert len(res["chunks"]) == 1
        assert res["chunks"][0]["relevanceScore"] == 0.5
    finally:
        app.dependency_overrides.clear()


def test_evaluate_endpoint_exception_handling():
    headers = {"X-Internal-Key": API_KEY}
    from src.main.main import app, get_decision_engine, SystemOneEngine

    class FailingDecisionEngine(SystemOneEngine):
        def evaluate_medical_report(self, *args, **kwargs):
            raise RuntimeError("Simulated Guardrail Engine Failure")

    app.dependency_overrides[get_decision_engine] = lambda: FailingDecisionEngine()
    try:
        payload = {
            "childContext": {"ageMonths": 12},
            "chunks": [],
            "reportText": "Текст",
            "recommendations": "Поради"
        }
        response = client.post("/evaluate", headers=headers, json=payload)
        assert response.status_code == 200
        res = response.json()
        assert res["passedGuardrail"] is True
        assert res["isSystemOne"] is False
    finally:
        app.dependency_overrides.clear()


def test_triage_chat_endpoint_exception_handling():
    headers = {"X-Internal-Key": API_KEY}
    from src.main.main import app, get_decision_engine, SystemOneEngine

    class FailingDecisionEngine(SystemOneEngine):
        def triage_chat_message(self, *args, **kwargs):
            raise RuntimeError("Simulated Chat Triage Engine Failure")

    app.dependency_overrides[get_decision_engine] = lambda: FailingDecisionEngine()
    try:
        payload = {
            "message": "Запитання",
            "childContext": {"ageMonths": 12}
        }
        response = client.post("/triage-chat", headers=headers, json=payload)
        assert response.status_code == 200
        res = response.json()
        assert res["intent"] == "SYMPTOM_OR_ILLNESS"
        assert res["needsRagSearch"] is True
    finally:
        app.dependency_overrides.clear()


# --- ТЕСТИ ЕДЖ-КЕЙСІВ FUZZY ENGINE & SYSTEM ONE ---

def test_fuzzy_engine_invalid_rule_condition():
    from src.main.fuzzy_engine import FuzzyEngine
    engine = FuzzyEngine()

    invalid_rule_payload = {
        "childId": "child-bad-rule",
        "ageMonths": 12,
        "rules": [
            {
                "conditions": [{"variable": "nonExistentVar", "term": "INVALID_TERM"}],
                "consequent": "HIGH"
            }
        ]
    }
    res = engine.compute_fuzzy_inference(invalid_rule_payload)
    assert res["isFallback"] is True


def test_fuzzy_engine_no_inputs_provided():
    from src.main.fuzzy_engine import FuzzyEngine
    engine = FuzzyEngine()

    payload = {
        "childId": "child-no-inputs",
        "rules": [
            {
                "conditions": [{"variable": "speechScore", "term": "LOW"}],
                "consequent": "HIGH"
            }
        ]
    }
    res = engine.compute_fuzzy_inference(payload)
    assert res["isFallback"] is True


def test_laya_model_driver_and_system_one_mocked(monkeypatch):
    monkeypatch.setenv("FAST_CHAT_TRIAGE", "false")
    from src.main.system_one import LayaModelDriver, SystemOneEngine

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

    assert driver.is_available is True

    engine = SystemOneEngine(driver=driver)
    assert engine.is_ready is True

    triage_res = engine.decide_clinical_triage({"childId": "c1"})
    assert triage_res["requiresDoctorConsultProbability"] == 0.85
    assert triage_res["primaryFocus"] == "REGRESSION_ALERT"
    assert triage_res["isSystemOne"] is True

    # Test rerank with mocked driver
    mock_rerank_agent = type("MockAgent", (), {
        "predict": lambda self, state, questions: {
            "relevance": {"score": 1.8},
            "is_safe_for_child": {"prob": 0.95}
        }
    })()
    driver._agent = mock_rerank_agent

    chunks = [{"title": "T1", "content": "C1"}]
    reranked = engine.rerank_and_filter_chunks("запитання", {"allergies": []}, chunks)
    assert len(reranked) == 1
    assert reranked[0]["relevanceScore"] == 0.90
    assert reranked[0]["isSafeAndApplicable"] is True

    # Test evaluate_medical_report with mocked driver
    mock_eval_agent = type("MockAgent", (), {
        "predict": lambda self, state, questions: {
            "faithfulness": {"score": 2.7},
            "relevance": {"score": 2.8},
            "no_diagnosis_or_prescription": {"prob": 0.95},
            "respects_allergies": {"prob": 0.95}
        }
    })()
    driver._agent = mock_eval_agent

    eval_res = engine.evaluate_medical_report({}, [], "Звіт", "Поради")
    assert eval_res["faithfulnessScore"] == 0.90
    assert eval_res["passedGuardrail"] is True
    assert eval_res["isSystemOne"] is True

    # Test triage_chat_message with mocked driver
    mock_chat_agent = type("MockAgent", (), {
        "predict": lambda self, state, questions: {
            "intent": "SYMPTOM_OR_ILLNESS",
            "urgency_level": "URGENT_RED_FLAG",
            "needs_rag_search": {"prob": 0.95},
            "asks_medication": {"prob": 0.05}
        }
    })()
    driver._agent = mock_chat_agent

    chat_res = engine.triage_chat_message("сильні судоми", {})
    assert chat_res["intent"] == "SYMPTOM_OR_ILLNESS"
    assert chat_res["urgencyLevel"] == "URGENT_RED_FLAG"
    assert chat_res["isSystemOne"] is True



