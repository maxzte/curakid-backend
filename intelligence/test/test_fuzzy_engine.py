from src.main.fuzzy_engine import FuzzyEngine


def test_fuzzy_engine_valid_rules_and_data(client, auth_headers):
    """Перевіряє успішний розрахунок нечіткої логіки з валідними правилами та даними."""
    # Given
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

    # When
    response = client.post("/analyze", headers=auth_headers, json=payload)

    # Then
    assert response.status_code == 200
    json_data = response.json()
    assert "urgencyScore" in json_data
    assert json_data["isFallback"] is False
    assert "breakdown" in json_data


def test_fuzzy_engine_fallback_trigger(client, auth_headers):
    """Перевіряє активацію фолбеку, якщо немає правил для моделювання."""
    # Given
    payload = {
        "childId": "child-789",
        "ageMonths": 6,
        "motorGrossScore": 50.0,
        "rules": []
    }

    # When
    response = client.post("/analyze", headers=auth_headers, json=payload)

    # Then
    assert response.status_code == 200
    json_data = response.json()
    assert json_data["isFallback"] is True
    assert "motor_gross" in json_data["breakdown"]


def test_fuzzy_engine_unused_inputs(client, auth_headers):
    """Перевіряє обробку змінних, не задіяних у правилах."""
    # Given
    payload = {
        "childId": "child-456",
        "ageMonths": 14,
        "gender": "MALE",
        "wfaZscore": -1.5,
        "hcfaZscore": 0.5,
        "rules": [
            {
                "conditions": [{"variable": "wfaZscore", "term": "LOW"}],
                "consequent": "MEDIUM"
            }
        ]
    }

    # When
    response = client.post("/analyze", headers=auth_headers, json=payload)

    # Then
    assert response.status_code == 200
    json_data = response.json()
    assert json_data["isFallback"] is False


def test_fuzzy_engine_trends_and_regression(client, auth_headers):
    """Перевіряє врахування динаміки зросту/ваги та регресу розвитку."""
    # Given
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

    # When
    response = client.post("/analyze", headers=auth_headers, json=payload)

    # Then
    assert response.status_code == 200
    json_data = response.json()
    assert json_data["urgencyScore"] >= 0.7
    assert "Динаміка ваги: Уповільнення" in json_data["linguisticInterpretations"]
    assert "Регрес розвитку: Виявлено" in json_data["linguisticInterpretations"]


def test_fuzzy_engine_age_specific_priority(client, auth_headers):
    """Перевіряє пріоритети терміновості залежно від віку дитини."""
    # Given
    rules = [
        {
            "conditions": [
                {"variable": "ageMonths", "term": "INFANT"},
                {"variable": "hcfaZscore", "term": "VERY_LOW"}
            ],
            "consequent": "HIGH"
        },
        {
            "conditions": [
                {"variable": "ageMonths", "term": "PRESCHOOL"},
                {"variable": "hcfaZscore", "term": "VERY_LOW"}
            ],
            "consequent": "MEDIUM"
        }
    ]

    # When
    res_infant = client.post("/analyze", headers=auth_headers, json={
        "childId": "infant", "ageMonths": 0, "hcfaZscore": -3.8, "rules": rules
    }).json()

    res_pre = client.post("/analyze", headers=auth_headers, json={
        "childId": "preschooler", "ageMonths": 60, "hcfaZscore": -3.8, "rules": rules
    }).json()

    # Then
    assert res_infant["urgencyScore"] > res_pre["urgencyScore"]


def test_fuzzy_engine_invalid_rule_condition():
    """Перевіряє обробку невалідних імен змінних у правилах."""
    # Given
    engine = FuzzyEngine()
    invalid_payload = {
        "childId": "bad-rule",
        "ageMonths": 12,
        "rules": [
            {
                "conditions": [{"variable": "nonExistentVar", "term": "INVALID_TERM"}],
                "consequent": "HIGH"
            }
        ]
    }

    # When
    res = engine.compute_fuzzy_inference(invalid_payload)

    # Then
    assert res["isFallback"] is True


def test_fuzzy_engine_no_inputs_provided():
    """Перевіряє фолбек, якщо жодна вхідна змінна з правил не передана у запиті."""
    # Given
    engine = FuzzyEngine()
    payload = {
        "childId": "no-inputs",
        "rules": [
            {
                "conditions": [{"variable": "speechScore", "term": "LOW"}],
                "consequent": "HIGH"
            }
        ]
    }

    # When
    res = engine.compute_fuzzy_inference(payload)

    # Then
    assert res["isFallback"] is True
