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
        # For Z-scores (твій код вже добре працює)
        for var_name, var in [('wfaZscore', wfa), ('hfaZscore', hfa),
                              ('bmiZscore', bmi), ('hcfaZscore', hcfa),
                              ('wfhZscore', wfh)]:
            n = (request.norms or {}).get(var_name, {})
            p3 = n.get('P3', -3.0)
            p15 = n.get('P15', -2.0)
            p50 = n.get('P50', 0.0)
            p85 = n.get('P85', 2.0)
            p97 = n.get('P97', 3.0)

            var['VERY_LOW'] = fuzz.trapmf(var.universe, [-4, -4, p3, p15])
            var['LOW'] = fuzz.trapmf(var.universe, [p3, p15, (p15 + p50) / 2, p50])
            var['NORMAL'] = fuzz.trapmf(var.universe, [p15, p50, p50, p85])
            var['HIGH'] = fuzz.trapmf(var.universe, [p50, (p50 + p85) / 2, p85, p97])
            var['VERY_HIGH'] = fuzz.trapmf(var.universe, [p85, p97, 4, 4])

        # For Skill scores - ДИНАМІЧНО з нормами
        for var_name, var in [('motorGrossScore', motor_gross),
                              ('motorFineScore', motor_fine),
                              ('speechScore', speech),
                              ('cognitiveScore', cognitive),
                              ('socialScore', social),
                              ('selfCareScore', self_care),
                              ('sensoryScore', sensory)]:
            # Отримати норми для цієї навички або використати дефолтні
            skill_norms = (request.norms or {}).get(var_name, {})

            # Дефолтні значення залежать від віку
            default_thresholds = get_default_skill_thresholds(request.ageMonths)

            critical_low = skill_norms.get('CRITICAL_LOW', default_thresholds['critical_low'])
            critical_high = skill_norms.get('CRITICAL_HIGH', default_thresholds['critical_high'])
            below_low = skill_norms.get('BELOW_LOW', default_thresholds['below_low'])
            below_high = skill_norms.get('BELOW_HIGH', default_thresholds['below_high'])
            normal_low = skill_norms.get('NORMAL_LOW', default_thresholds['normal_low'])

            var['CRITICAL'] = fuzz.trapmf(
                var.universe,
                [0, 0, critical_low, critical_high]
            )
            var['BELOW_NORMAL'] = fuzz.trapmf(
                var.universe,
                [critical_high, below_low, below_high, normal_low]
            )
            var['NORMAL'] = fuzz.trapmf(
                var.universe,
                [below_high, normal_low, 100, 100]
            )

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

        # Build linguistic interpretations
        interpretations = []
        term_translation = {
            'VERY_LOW': 'Дуже низький', 'LOW': 'Низький', 'NORMAL': 'Норма',
            'HIGH': 'Високий', 'VERY_HIGH': 'Дуже високий',
            'CRITICAL': 'Критичний рівень', 'BELOW_NORMAL': 'Нижче норми',
            'MEDIUM': 'Середній'
        }
        var_translation = {
            'motorGrossScore': 'Груба моторика', 'motorFineScore': 'Дрібна моторика',
            'speechScore': 'Мовлення', 'cognitiveScore': 'Когнітивні навички',
            'socialScore': 'Соціальні навички', 'selfCareScore': 'Самообслуговування',
            'sensoryScore': 'Сенсорика', 'wfaZscore': 'Вага до віку',
            'hfaZscore': 'Ріст до віку', 'bmiZscore': 'ІМТ',
            'hcfaZscore': 'Окружність голови', 'wfhZscore': 'Вага до росту'
        }

        for key, var in vars_map.items():
            val = getattr(request, key, None)
            if val is not None:
                # Find the term with highest membership
                best_term = None
                max_membership = 0
                for term in var.terms:
                    m = fuzz.interp_membership(var.universe, var[term].mf, float(val))
                    if m > max_membership:
                        max_membership = m
                        best_term = term

                if best_term and max_membership > 0.5:
                    label = var_translation.get(key, key)
                    term_label = term_translation.get(best_term, best_term)
                    interpretations.append(f"{label}: {term_label}")

        # Build breakdown (simplified for now)
        breakdown = {
            k: round(float(fuzz.interp_membership(v.universe, v[list(v.terms.keys())[0]].mf, getattr(request, k) or 0)),
                     2)
            for k, v in vars_map.items() if getattr(request, k) is not None}

        return {
            "urgencyScore": round(float(res_urgency), 2),
            "isFallback": False,
            "breakdown": breakdown,
            "linguisticInterpretations": interpretations
        }

    except Exception as e:
        logger.error(f"Error during FIS analysis: {str(e)}")
        return await analyze_fallback(request)


def get_default_skill_thresholds(age_months: int) -> Dict[str, float]:
    """
    Повертає дефолтні пороги для навичок на основі віку.
    Молодші діти мають м'якші пороги.
    """
    if age_months <= 12:  # Немовлята 0-12 місяців
        return {
            'critical_low': 15.0,
            'critical_high': 30.0,
            'below_low': 25.0,
            'below_high': 50.0,
            'normal_low': 60.0
        }
    elif age_months <= 24:  # 1-2 роки
        return {
            'critical_low': 20.0,
            'critical_high': 35.0,
            'below_low': 30.0,
            'below_high': 55.0,
            'normal_low': 70.0
        }
    elif age_months <= 36:  # 2-3 роки
        return {
            'critical_low': 25.0,
            'critical_high': 40.0,
            'below_low': 35.0,
            'below_high': 60.0,
            'normal_low': 75.0
        }
    else:  # 3-6 років
        return {
            'critical_low': 30.0,
            'critical_high': 45.0,
            'below_low': 40.0,
            'below_high': 65.0,
            'normal_low': 80.0
        }


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
        "breakdown": breakdown,
        "linguisticInterpretations": []
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
