from src.main.main import app, get_fuzzy_engine, get_decision_engine, FuzzyEngine, SystemOneEngine


def test_analyze_with_system_one_triage(client, auth_headers):
    """Перевіряє гібридний аналіз /analyze з інтеграцією System 1."""
    # Given
    payload = {
        "childId": "child-s1",
        "ageMonths": 18,
        "wfaZscore": -2.5,
        "allergies": ["молоко"],
        "notes": ["малюк млявий"],
        "regressions": {"SPEECH": True},
        "rules": []
    }

    # When
    response = client.post("/analyze", headers=auth_headers, json=payload)

    # Then
    assert response.status_code == 200
    data = response.json()
    assert "systemOneDecisions" in data
    assert data["urgencyScore"] >= 0.5


def test_rerank_endpoint(client, auth_headers):
    """Перевіряє фільтрацію небезпечних чанків у /rerank."""
    # Given
    payload = {
        "query": "харчування 1 рік",
        "childContext": {"ageMonths": 12, "allergies": ["молоко"]},
        "chunks": [
            {"title": "Грудне вигодовування", "content": "Загальні норми."},
            {"title": "Молочні продукти", "content": "Вживати незбиране молоко."}
        ]
    }

    # When
    response = client.post("/rerank", headers=auth_headers, json=payload)

    # Then
    assert response.status_code == 200
    reranked = response.json()["chunks"]
    assert len(reranked) >= 1
    titles = [c["title"] for c in reranked]
    assert "Грудне вигодовування" in titles


def test_analyze_endpoint_exception_handling(client, auth_headers):
    """Перевіряє граціозний фолбек при винятку в FuzzyEngine під час /analyze."""
    # Given
    class FailingFuzzyEngine(FuzzyEngine):
        def compute_fuzzy_inference(self, request_data):
            raise RuntimeError("Simulated Fuzzy Engine Failure")

    app.dependency_overrides[get_fuzzy_engine] = lambda: FailingFuzzyEngine()

    try:
        payload = {"childId": "err-child", "ageMonths": 12, "rules": []}

        # When
        response = client.post("/analyze", headers=auth_headers, json=payload)

        # Then
        assert response.status_code == 200
        res = response.json()
        assert res["isFallback"] is True
    finally:
        app.dependency_overrides.clear()


def test_rerank_endpoint_exception_handling(client, auth_headers):
    """Перевіряє безпечний фолбек при винятку під час /rerank."""
    # Given
    class FailingDecisionEngine(SystemOneEngine):
        def rerank_and_filter_chunks(self, *args, **kwargs):
            raise RuntimeError("Simulated Rerank Failure")

    app.dependency_overrides[get_decision_engine] = lambda: FailingDecisionEngine()

    try:
        payload = {
            "query": "тест",
            "childContext": {"ageMonths": 12},
            "chunks": [{"title": "T1", "content": "C1"}]
        }

        # When
        response = client.post("/rerank", headers=auth_headers, json=payload)

        # Then
        assert response.status_code == 200
        res = response.json()
        assert len(res["chunks"]) == 1
        assert res["chunks"][0]["relevanceScore"] == 0.5
    finally:
        app.dependency_overrides.clear()


def test_evaluate_endpoint_exception_handling(client, auth_headers):
    """Перевіряє безпечний фолбек при винятку під час /evaluate."""
    # Given
    class FailingDecisionEngine(SystemOneEngine):
        def evaluate_medical_report(self, *args, **kwargs):
            raise RuntimeError("Simulated Guardrail Failure")

    app.dependency_overrides[get_decision_engine] = lambda: FailingDecisionEngine()

    try:
        payload = {
            "childContext": {"ageMonths": 12},
            "chunks": [],
            "reportText": "Текст",
            "recommendations": "Поради"
        }

        # When
        response = client.post("/evaluate", headers=auth_headers, json=payload)

        # Then
        assert response.status_code == 200
        res = response.json()
        assert res["passedGuardrail"] is True
        assert res["isSystemOne"] is False
    finally:
        app.dependency_overrides.clear()


def test_triage_chat_endpoint_exception_handling(client, auth_headers):
    """Перевіряє безпечний фолбек при винятку під час /triage-chat."""
    # Given
    class FailingDecisionEngine(SystemOneEngine):
        def triage_chat_message(self, *args, **kwargs):
            raise RuntimeError("Simulated Chat Triage Failure")

    app.dependency_overrides[get_decision_engine] = lambda: FailingDecisionEngine()

    try:
        payload = {
            "message": "Запитання",
            "childContext": {"ageMonths": 12}
        }

        # When
        response = client.post("/triage-chat", headers=auth_headers, json=payload)

        # Then
        assert response.status_code == 200
        res = response.json()
        assert res["intent"] == "SYMPTOM_OR_ILLNESS"
        assert res["needsRagSearch"] is True
    finally:
        app.dependency_overrides.clear()
