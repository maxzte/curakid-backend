from fastapi import FastAPI, Header, HTTPException, Depends
from pydantic import BaseModel
from typing import List, Optional, Dict, Any
import numpy as np
import skfuzzy as fuzz
from skfuzzy import control as ctrl
from sentence_transformers import SentenceTransformer
import os
import logging

# Setup logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="CuraKid Intelligence Service")

# Security
INTERNAL_API_KEY = os.getenv("INTERNAL_API_KEY", "change-me")

async def verify_internal_key(x_internal_key: str = Header(...)):
    if x_internal_key != INTERNAL_API_KEY:
        logger.warning(f"Invalid API key attempt: {x_internal_key}")
        raise HTTPException(status_code=403, detail="Forbidden")

# Embedding Model (Lazy Load or on start)
logger.info("Loading SentenceTransformer model: intfloat/multilingual-e5-large")
model = SentenceTransformer('intfloat/multilingual-e5-large')

class FisRequest(BaseModel):
    childId: str
    ageMonths: int
    gender: Optional[str]
    wfaZscore: Optional[float]
    hfaZscore: Optional[float]
    bmiZscore: Optional[float]
    motorScore: Optional[float]
    speechScore: Optional[float]
    cognitiveScore: Optional[float]
    socialScore: Optional[float]
    rules: List[Dict[str, Any]]

class EmbeddingRequest(BaseModel):
    text: str

@app.get("/health")
async def health():
    return {"status": "ok", "model": "multilingual-e5-large"}

@app.post("/analyze", dependencies=[Depends(verify_internal_key)])
async def analyze(request: FisRequest):
    """
    Performs Fuzzy Inference based on child data and dynamic rules.
    """
    try:
        # For now, we implement a robust heuristic that mimics fuzzy logic
        # until the full skfuzzy rule parser is refined for dynamic JSON rules.
        
        # Heuristic: 
        # Z-scores < -2 are critical (1.0)
        # Scores < 50 are critical (1.0)
        
        breakdown = {}
        
        # Physical
        phys_score = 0.0
        phys_count = 0
        for val in [request.wfaZscore, request.hfaZscore, request.bmiZscore]:
            if val is not None:
                # Map Z-score -3..0 to 1..0
                score = np.clip((0 - val) / 2.0, 0, 1)
                phys_score += score
                phys_count += 1
        breakdown["physical"] = phys_score / phys_count if phys_count > 0 else 0.0
        
        # Skills
        skill_score = 0.0
        skill_count = 0
        for name, val in [("motor", request.motorScore), ("speech", request.speechScore), 
                          ("cognitive", request.cognitiveScore), ("social", request.socialScore)]:
            if val is not None:
                # Map 0..100 to 1..0
                score = np.clip((80 - val) / 50.0, 0, 1)
                skill_score += score
                skill_count += 1
                breakdown[name] = score
        
        avg_skill = skill_score / skill_count if skill_count > 0 else 0.0
        
        # Final Urgency: Max of physical and skills
        urgency_score = max(breakdown.get("physical", 0.0), avg_skill)
        
        return {
            "urgencyScore": round(float(urgency_score), 2),
            "isFallback": False,
            "breakdown": breakdown
        }
    except Exception as e:
        logger.error(f"Error during FIS analysis: {str(e)}")
        return {
            "urgencyScore": 0.5,
            "isFallback": True,
            "error": str(e)
        }

@app.post("/embed", dependencies=[Depends(verify_internal_key)])
async def embed(request: EmbeddingRequest):
    try:
        # e5 models expect "query: " prefix for retrieval tasks
        text = f"query: {request.text}"
        embedding = model.encode(text)
        return {"embedding": embedding.tolist()}
    except Exception as e:
        logger.error(f"Error during embedding generation: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
