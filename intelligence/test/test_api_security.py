def test_health_endpoint(client):
    """Перевіряє публічний health check ендпоїнт."""
    # Given / When
    response = client.get("/health")

    # Then
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_secured_endpoint_without_header(client):
    """Перевіряє, що запит без заголовку безпеки повертає помилку валідації 422."""
    # Given
    valid_payload = {"childId": "test-child", "ageMonths": 12, "rules": []}

    # When
    response = client.post("/analyze", json=valid_payload)

    # Then
    assert response.status_code == 422


def test_secured_endpoint_with_invalid_header(client):
    """Перевіряє, що невалідний X-Internal-Key повертає HTTP 403 Forbidden."""
    # Given
    valid_payload = {"childId": "test-child", "ageMonths": 12, "rules": []}
    headers = {"X-Internal-Key": "wrong-key"}

    # When
    response = client.post("/analyze", headers=headers, json=valid_payload)

    # Then
    assert response.status_code == 403
    assert response.json()["detail"] == "Forbidden"
