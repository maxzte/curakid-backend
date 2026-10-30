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

