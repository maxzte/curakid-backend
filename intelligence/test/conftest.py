import os
import pytest
from fastapi.testclient import TestClient

# Ensure tests run in offline mode without attempting network downloads for HuggingFace models
os.environ["HF_HUB_OFFLINE"] = "1"
os.environ["TRANSFORMERS_OFFLINE"] = "1"

from src.main.main import app

API_KEY = "test-secret-key"


@pytest.fixture(autouse=True)
def setup_env(monkeypatch):
    """Автоматично виставляє тестовий API-ключ та офлайн-режим для кожного тесту."""
    monkeypatch.setenv("INTERNAL_API_KEY", API_KEY)
    monkeypatch.setenv("HF_HUB_OFFLINE", "1")
    monkeypatch.setenv("TRANSFORMERS_OFFLINE", "1")
    import src.main.main
    monkeypatch.setattr(src.main.main, "INTERNAL_API_KEY", API_KEY)


@pytest.fixture
def client():
    """Фікстура для FastAPI TestClient."""
    return TestClient(app)


@pytest.fixture
def auth_headers():
    """Фікстура для аутентифікованого заголовку запиту."""
    return {"X-Internal-Key": API_KEY}
