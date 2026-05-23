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
    hcfaZscore: Optional[float]
    wfhZscore: Optional[float]
    motorGrossScore: Optional[float]
    motorFineScore: Optional[float]
    speechScore: Optional[float]
    cognitiveScore: Optional[float]
    socialScore: Optional[float]
    selfCareScore: Optional[float]
    sensoryScore: Optional[float]
    rules: List[Dict[str, Any]]
    norms: Optional[Dict[str, Dict[str, float]]] = {}

class EmbeddingRequest(BaseModel):
    text: str

@app.get("/health")
async def health():
    return {"status": "ok", "model": "multilingual-e5-large"}

@app.post("/analyze", dependencies=[Depends(verify_internal_key)])
async def analyze(request: FisRequest):
    """
    Performs Fuzzy Inference based on child data and dynamic rules from DB.
    """
    try:
        # 1. Define Antecedents (Inputs)
        # Z-scores: range -4 to 4
        z_range = np.arange(-4, 4.1, 0.1)
        wfa = ctrl.Antecedent(z_range, 'wfaZscore')
        hfa = ctrl.Antecedent(z_range, 'hfaZscore')
        bmi = ctrl.Antecedent(z_range, 'bmiZscore')
        hcfa = ctrl.Antecedent(z_range, 'hcfaZscore')
        wfh = ctrl.Antecedent(z_range, 'wfhZscore')

        # Skill scores: range 0 to 100
        s_range = np.arange(0, 101, 1)
        motor_gross = ctrl.Antecedent(s_range, 'motorGrossScore')
        motor_fine = ctrl.Antecedent(s_range, 'motorFineScore')
        speech = ctrl.Antecedent(s_range, 'speechScore')
        cognitive = ctrl.Antecedent(s_range, 'cognitiveScore')
        social = ctrl.Antecedent(s_range, 'socialScore')
        self_care = ctrl.Antecedent(s_range, 'selfCareScore')
        sensory = ctrl.Antecedent(s_range, 'sensoryScore')

        # 2. Define Consequent (Output)
        u_range = np.arange(0, 1.01, 0.01)
        urgency = ctrl.Consequent(u_range, 'urgency')

        # 3. Define Trapezoidal Membership Functions
        # For Z-scores
        for var_name, var in [('wfaZscore', wfa), ('hfaZscore', hfa), ('bmiZscore', bmi), ('hcfaZscore', hcfa), ('wfhZscore', wfh)]:
            n = (request.norms or {}).get(var_name, {})
            # Use provided norms or fall back to standard Z-score thresholds
            p3 = n.get('P3', -3.0)
            p15 = n.get('P15', -2.0)
            p50 = n.get('P50', 0.0)
            p85 = n.get('P85', 2.0)
            p97 = n.get('P97', 3.0)

            var['VERY_LOW'] = fuzz.trapmf(var.universe, [-4, -4, p3, p15])
            var['LOW'] = fuzz.trapmf(var.universe, [p3, p15, (p15 + p50)/2, p50])
            var['NORMAL'] = fuzz.trapmf(var.universe, [p15, p50, p50, p85])
            var['HIGH'] = fuzz.trapmf(var.universe, [p50, (p50 + p85)/2, p85, p97])
            var['VERY_HIGH'] = fuzz.trapmf(var.universe, [p85, p97, 4, 4])

        # For Skill scores
        for var in [motor_gross, motor_fine, speech, cognitive, social, self_care, sensory]:
            var['CRITICAL'] = fuzz.trapmf(var.universe, [0, 0, 20, 40])
            var['BELOW_NORMAL'] = fuzz.trapmf(var.universe, [30, 40, 60, 75])
            var['NORMAL'] = fuzz.trapmf(var.universe, [70, 85, 100, 100])

        # For Urgency
        urgency['LOW'] = fuzz.trapmf(urgency.universe, [0, 0, 0.2, 0.4])
        urgency['MEDIUM'] = fuzz.trapmf(urgency.universe, [0.3, 0.4, 0.6, 0.7])
        urgency['HIGH'] = fuzz.trapmf(urgency.universe, [0.6, 0.8, 1, 1])

        # 4. Map Variables for dynamic lookup
        vars_map = {
            'wfaZscore': wfa, 'hfaZscore': hfa, 'bmiZscore': bmi,
            'hcfaZscore': hcfa, 'wfhZscore': wfh,
            'motorGrossScore': motor_gross, 'motorFineScore': motor_fine,
            'speechScore': speech, 'cognitiveScore': cognitive,
            'socialScore': social, 'selfCareScore': self_care,
            'sensoryScore': sensory
        }

        # 5. Build Rules dynamically
        rules_list = []
        for r in request.rules:
            try:
                # Build IF part (AND conditions)
                cond_objs = []
                for c in r['conditions']:
                    var_obj = vars_map.get(c['variable'])
                    if var_obj:
                        cond_objs.append(var_obj[c['term']])
                
                if not cond_objs:
                    continue

                if len(cond_objs) == 1:
                    if_part = cond_objs[0]
                else:
                    if_part = cond_objs[0]
                    for next_cond in cond_objs[1:]:
                        if_part &= next_cond
                
                # Build THEN part
                then_part = urgency[r['consequent']]
                
                rules_list.append(ctrl.Rule(if_part, then_part))
            except Exception as rule_err:
                logger.warning(f"Skipping invalid rule: {r}, error: {rule_err}")

        if not rules_list:
            # Fallback to default heuristic if no rules defined
            return await analyze_fallback(request)

        # 6. Computation
        fis_ctrl = ctrl.ControlSystem(rules_list)
        fis_sim = ctrl.ControlSystemSimulation(fis_ctrl)

        # Set inputs
        inputs_provided = False
        for key, var in vars_map.items():
            val = getattr(request, key, None)
            if val is not None:
                fis_sim.input[key] = float(val)
                inputs_provided = True

        if not inputs_provided:
            return {"urgencyScore": 0.0, "isFallback": False, "breakdown": {}}

        fis_sim.compute()
        res_urgency = fis_sim.output['urgency']

        # Build breakdown (simplified for now)
        breakdown = {k: round(float(fuzz.interp_membership(v.universe, v[list(v.terms.keys())[0]].mf, getattr(request, k) or 0)), 2) 
                     for k, v in vars_map.items() if getattr(request, k) is not None}

        return {
            "urgencyScore": round(float(res_urgency), 2),
            "isFallback": False,
            "breakdown": breakdown
        }

    except Exception as e:
        logger.error(f"Error during FIS analysis: {str(e)}")
        return await analyze_fallback(request)

async def analyze_fallback(request: FisRequest):
    # Keep the previous heuristic as a safety fallback
    breakdown = {}
    skill_score = 0.0
    skill_count = 0
    domains = [("motor_gross", request.motorGrossScore), ("motor_fine", request.motorFineScore),
               ("speech", request.speechScore), ("cognitive", request.cognitiveScore),
               ("social", request.socialScore), ("self_care", request.selfCareScore),
               ("sensory", request.sensoryScore)]
    
    for name, val in domains:
        if val is not None:
            score = np.clip((80 - val) / 50.0, 0, 1)
            skill_score += score
            skill_count += 1
            breakdown[name] = round(float(score), 2)
    
    avg_skill = skill_score / skill_count if skill_count > 0 else 0.0
    urgency_score = max(avg_skill, 0.0)
    
    return {
        "urgencyScore": round(float(urgency_score), 2),
        "isFallback": True,
        "breakdown": breakdown
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
