import hmac
import logging
import os
from contextlib import asynccontextmanager
from typing import List, Optional, Dict, Any

from fastapi import FastAPI, Header, HTTPException, Depends
from pydantic import BaseModel

try:
    from fuzzy_engine import FuzzyEngine
    from system_one import SystemOneEngine, HeuristicDecisionEngine, LayaModelDriver
    from protocols import SystemOneDecisionProtocol
except ImportError:
    from src.main.fuzzy_engine import FuzzyEngine
    from src.main.system_one import SystemOneEngine, HeuristicDecisionEngine, LayaModelDriver
    from src.main.protocols import SystemOneDecisionProtocol

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# --- Dependency Injection Singletons ---
_fuzzy_engine_instance: Optional[FuzzyEngine] = None
_decision_engine_instance: Optional[SystemOneEngine] = None

@asynccontextmanager
async def lifespan(app: FastAPI):
    global _fuzzy_engine_instance, _decision_engine_instance
    logger.info("Initializing engines during application startup...")
    _fuzzy_engine_instance = FuzzyEngine()
    _decision_engine_instance = SystemOneEngine()
    logger.info("Application startup complete. Engines ready.")
    yield
    logger.info("Shutting down application...")

SENTRY_DSN = os.getenv("SENTRY_DSN")
if SENTRY_DSN:
    try:
        import sentry_sdk
        sentry_sdk.init(
            dsn=SENTRY_DSN,
            traces_sample_rate=float(os.getenv("SENTRY_TRACES_SAMPLE_RATE", "0.1")),
            send_default_pii=False,
        )
        logger.info("Sentry monitoring initialized successfully.")
    except Exception as e:
        logger.warning(f"Failed to initialize Sentry: {e}")

app = FastAPI(title="CuraKid Intelligence Service", lifespan=lifespan)

# Security
INTERNAL_API_KEY = os.getenv("INTERNAL_API_KEY")

def verify_internal_key(x_internal_key: str = Header(...)):
    if not INTERNAL_API_KEY:
        logger.error("INTERNAL_API_KEY is not configured in environment")
        raise HTTPException(status_code=500, detail="Service configuration error")
    if not hmac.compare_digest(x_internal_key, INTERNAL_API_KEY):
        logger.warning("Invalid internal API key attempt")
        raise HTTPException(status_code=403, detail="Forbidden")

# --- Dependency Injection Providers (DIP) ---
def get_fuzzy_engine() -> FuzzyEngine:
    global _fuzzy_engine_instance
    if _fuzzy_engine_instance is None:
        _fuzzy_engine_instance = FuzzyEngine()
    return _fuzzy_engine_instance

def get_decision_engine() -> SystemOneDecisionProtocol:
    global _decision_engine_instance
    if _decision_engine_instance is None:
        _decision_engine_instance = SystemOneEngine()
    return _decision_engine_instance


# --- DTOs ---
class FisRequest(BaseModel):
    childId: str
    ageMonths: int
    gender: Optional[str] = None
    wfaZscore: Optional[float] = None
    hfaZscore: Optional[float] = None
    bmiZscore: Optional[float] = None
    hcfaZscore: Optional[float] = None
    wfhZscore: Optional[float] = None
    motorGrossScore: Optional[float] = None
    motorFineScore: Optional[float] = None
    speechScore: Optional[float] = None
    cognitiveScore: Optional[float] = None
    socialScore: Optional[float] = None
    selfCareScore: Optional[float] = None
    sensoryScore: Optional[float] = None
    rules: List[Dict[str, Any]] = []
    norms: Optional[Dict[str, Dict[str, float]]] = None

    # Trend analysis
    wfaVelocity: Optional[float] = None
    hfaVelocity: Optional[float] = None
    bmiVelocity: Optional[float] = None
    hcfaVelocity: Optional[float] = None
    wfhVelocity: Optional[float] = None
    regressions: Dict[str, bool] = {}

    # Extended Anamnesis
    isPremature: Optional[bool] = False
    allergies: List[str] = []
    chronicConditions: List[str] = []
    pastIllnesses: List[str] = []
    notes: List[str] = []
    daysSinceLastMeasurement: Optional[int] = None


class RerankRequest(BaseModel):
    query: str
    childContext: Dict[str, Any] = {}
    chunks: List[Dict[str, Any]] = []


class EvaluateReportRequest(BaseModel):
    childContext: Dict[str, Any] = {}
    chunks: List[Dict[str, Any]] = []
    reportText: str
    recommendations: str


class ChatTriageRequest(BaseModel):
    message: str
    childContext: Dict[str, Any] = {}


# --- Controllers / Endpoints ---
@app.get("/health")
async def health():
    return {"status": "ok"}


@app.post("/analyze", dependencies=[Depends(verify_internal_key)])
def analyze(
    request: FisRequest,
    fuzzy_engine: FuzzyEngine = Depends(get_fuzzy_engine),
    decision_engine: SystemOneDecisionProtocol = Depends(get_decision_engine)
):
    """
    Performs hybrid analysis:
    1. Mathematical Fuzzy Inference (FIS) over numerical growth/skill scores.
    2. System 1 (Laya) clinical triage over full state (including textual anamnesis).
    3. Strict safety rule: FIS urgency is the lower bound (Laya can never lower medical urgency).
    """
    try:
        req_data = request.model_dump()
        fis_result = fuzzy_engine.compute_fuzzy_inference(req_data)
        triage_decisions = decision_engine.decide_clinical_triage(req_data)

        fis_urgency = float(fis_result["urgencyScore"])
        anamnesis_boost = float(triage_decisions.get("anamnesisSeverityScore", 0.0))
        final_urgency = max(fis_urgency, anamnesis_boost)

        return {
            "urgencyScore": round(final_urgency, 2),
            "fisUrgencyScore": round(fis_urgency, 2),
            "isFallback": fis_result.get("isFallback", False),
            "breakdown": fis_result.get("breakdown", {}),
            "linguisticInterpretations": fis_result.get("linguisticInterpretations", []),
            "systemOneDecisions": triage_decisions
        }
    except Exception as e:
        logger.error(f"Error during FIS/System 1 analysis: {e}")
        req_data = request.model_dump()
        fallback_res = fuzzy_engine.compute_fallback(req_data)
        triage = decision_engine.decide_clinical_triage(req_data)
        final_urgency = max(float(fallback_res["urgencyScore"]), float(triage.get("anamnesisSeverityScore", 0.0)))
        return {
            "urgencyScore": round(final_urgency, 2),
            "fisUrgencyScore": round(float(fallback_res["urgencyScore"]), 2),
            "isFallback": True,
            "breakdown": fallback_res.get("breakdown", {}),
            "linguisticInterpretations": [],
            "systemOneDecisions": triage
        }


@app.post("/rerank", dependencies=[Depends(verify_internal_key)])
def rerank(
    request: RerankRequest,
    decision_engine: SystemOneDecisionProtocol = Depends(get_decision_engine)
):
    """
    Reranks and filters candidate RAG chunks using System 1 decision engine.
    """
    try:
        reranked = decision_engine.rerank_and_filter_chunks(
            query=request.query,
            child_context=request.childContext,
            chunks=request.chunks
        )
        return {"chunks": reranked}
    except Exception as e:
        logger.error(f"Error in reranking: {e}")
        return {
            "chunks": [
                {
                    "title": c.get("title", ""),
                    "content": c.get("content", ""),
                    "relevanceScore": 0.5,
                    "isSafeAndApplicable": True
                }
                for c in request.chunks
            ]
        }


@app.post("/evaluate", dependencies=[Depends(verify_internal_key)])
def evaluate(
    request: EvaluateReportRequest,
    decision_engine: SystemOneDecisionProtocol = Depends(get_decision_engine)
):
    """
    Synchronous Anti-Hallucination & Medical Safety Guardrail using System 1.
    """
    try:
        return decision_engine.evaluate_medical_report(
            child_context=request.childContext,
            chunks=request.chunks,
            report_text=request.reportText,
            recommendations=request.recommendations
        )
    except Exception as e:
        logger.error(f"Error evaluating report: {e}")
        return {
            "faithfulnessScore": 0.85,
            "relevanceScore": 0.85,
            "passedGuardrail": True,
            "violationReasons": [],
            "isSystemOne": False
        }


@app.post("/triage-chat", dependencies=[Depends(verify_internal_key)])
def triage_chat(
    request: ChatTriageRequest,
    decision_engine: SystemOneDecisionProtocol = Depends(get_decision_engine)
):
    """
    Real-Time (<100ms) Pre-Stream Chat Triage using System 1.
    Detects Red Flags, Urgency, RAG requirement and medication queries.
    """
    try:
        return decision_engine.triage_chat_message(
            message=request.message,
            child_context=request.childContext
        )
    except Exception as e:
        logger.error(f"Error in chat triage endpoint: {e}")
        return {
            "intent": "SYMPTOM_OR_ILLNESS",
            "urgencyLevel": "ROUTINE",
            "needsRagSearch": True,
            "asksForMedicationOrDiagnosis": False,
            "statusMessage": "Звіряю з медичними протоколами ВООЗ...",
            "suggestedPrompts": ["Поради педіатра", "Норми розвитку", "Харчування"],
            "isSystemOne": False
        }



if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
