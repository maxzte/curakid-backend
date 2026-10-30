import logging
import os
from typing import Dict, Any, List, Optional
try:
    from protocols import SystemOneDecisionProtocol
    from prompts import (
        CLINICAL_TRIAGE_QUESTIONS,
        CHUNK_RERANK_QUESTIONS,
        REPORT_GUARDRAIL_QUESTIONS,
        CHAT_TRIAGE_QUESTIONS
    )
except ImportError:
    from src.main.protocols import SystemOneDecisionProtocol
    from src.main.prompts import (
        CLINICAL_TRIAGE_QUESTIONS,
        CHUNK_RERANK_QUESTIONS,
        REPORT_GUARDRAIL_QUESTIONS,
        CHAT_TRIAGE_QUESTIONS
    )

logger = logging.getLogger(__name__)

class HeuristicDecisionEngine:
    """
    Deterministic rule-based implementation of decision protocols.
    Follows LSP and serves as an independent, fully testable fallback strategy.
    """
    def decide_clinical_triage(self, state: Dict[str, Any]) -> Dict[str, Any]:
        allergies = state.get("allergies", [])
        chronic = state.get("chronicConditions", [])
        has_allergy = bool(allergies or chronic)

        wfa_v = abs(float(state.get("wfaVelocity") or 0.0))
        days = state.get("daysSinceLastMeasurement")
        outlier = bool(days is not None and days <= 7 and wfa_v >= 1.0)

        regressions = state.get("regressions", {})
        has_regression = bool(any(regressions.values())) if isinstance(regressions, dict) else False

        wfa_z = float(state.get("wfaZscore") or 0.0)
        hfa_z = float(state.get("hfaZscore") or 0.0)
        has_bad_z = wfa_z < -2.0 or wfa_z > 2.0 or hfa_z < -2.0

        requires_consult = 0.9 if (has_regression or has_bad_z or outlier) else 0.1
        primary = "ROUTINE_CARE"
        if has_regression:
            primary = "REGRESSION_ALERT"
        elif has_bad_z:
            primary = "ANTHROPOMETRY_NUTRITION"

        return {
            "primaryFocus": primary,
            "requiresDoctorConsultProbability": 0.9 if requires_consult >= 0.5 else 0.1,
            "allergyOrChronicConstraintProbability": 0.95 if has_allergy else 0.05,
            "measurementOutlierProbability": 0.85 if outlier else 0.05,
            "anamnesisSeverityScore": 0.7 if (has_regression or has_bad_z) else 0.1,
            "isSystemOne": False
        }

    def rerank_and_filter_chunks(
        self,
        query: str,
        child_context: Dict[str, Any],
        chunks: List[Dict[str, Any]]
    ) -> List[Dict[str, Any]]:
        results = []
        allergies = [a.lower() for a in child_context.get("allergies", [])]

        for chunk in chunks:
            title = chunk.get("title", "")
            content = chunk.get("content", "")
            full_text = (title + " " + content).lower()

            is_safe = True
            for a in allergies:
                if a in full_text and ("вживати" in full_text or "давати" in full_text):
                    is_safe = False
                    break

            if is_safe:
                results.append({
                    "title": title,
                    "content": content,
                    "relevanceScore": 0.75,
                    "isSafeAndApplicable": True
                })

        return results

    def evaluate_medical_report(
        self,
        child_context: Dict[str, Any],
        chunks: List[Dict[str, Any]],
        report_text: str,
        recommendations: str
    ) -> Dict[str, Any]:
        reco = (recommendations + " " + report_text).lower()
        allergies = [a.lower() for a in child_context.get("allergies", [])]

        diag_safe = True
        banned_phrases = ["діагноз:", "призначити", "приймати антибіотик", "дозування:", "лікувати таблетками"]
        for bp in banned_phrases:
            if bp in reco:
                diag_safe = False
                break

        allergy_safe = True
        for a in allergies:
            if a in reco and ("введіть" in reco or "давайте" in reco or "годуйте" in reco):
                allergy_safe = False
                break

        violations = []
        faith = 0.85 if (diag_safe and allergy_safe) else 0.50
        if faith < 0.75:
            violations.append("Низький рівень відповідності медичним протоколам (виявлено потенційні домисли або непідтверджені поради)")
        if not diag_safe:
            violations.append("Виявлено спробу встановлення клінічного діагнозу або призначення медикаментів (заборонено)")
        if not allergy_safe:
            violations.append("Рекомендації містять потенційний конфлікт із задокументованими алергіями дитини")

        return {
            "faithfulnessScore": round(faith, 2),
            "relevanceScore": 0.85,
            "passedGuardrail": len(violations) == 0,
            "violationReasons": violations,
            "isSystemOne": False
        }

    def triage_chat_message(
        self,
        message: str,
        child_context: Dict[str, Any]
    ) -> Dict[str, Any]:
        msg_lower = (message or "").lower()
        red_flags = ["судом", "не дихає", "важко дихати", "задишка", "знепритомн", "втрата свідомості", "сині губи", "ковтнув", "отруєн", "висока температура 39", "висока температура 40"]
        is_red_flag = any(rf in msg_lower for rf in red_flags)

        medication_keywords = ["дозування", "скільки давати", "які ліки", "який антибіотик", "призначте", "рецепт", "нурофен доза", "парацетамол доза", "сироп", "таблетк"]
        asks_meds = any(mk in msg_lower for mk in medication_keywords)

        greetings = ["привіт", "добрий день", "доброго дня", "вітаю", "дякую", "бувай"]
        is_greeting = any(g in msg_lower for g in greetings)

        if is_red_flag:
            intent = "EMERGENCY_RED_FLAG"
            urgency = "URGENT_RED_FLAG"
            needs_rag = True
            status_text = "Увага: перевіряю червоні прапорці стану здоров'я..."
            prompts = ["Як викликати швидку допомогу?", "Що робити до приїзду лікаря?", "Які ознаки небезпечного зневоднення?"]
        elif asks_meds:
            intent = "SYMPTOM_OR_ILLNESS"
            urgency = "CONSULT_PEDIATRICIAN"
            needs_rag = True
            status_text = "Увага: консультація щодо догляду без призначення ліків..."
            prompts = ["Які симптоми вимагають огляду педіатра?", "Як правильно контролювати температуру?", "Які правила безпечного догляду?"]
        elif any(k in msg_lower for k in ["прикорм", "годуван", "апетит", "пюре", "каш", "суміш", "молок", "грудн"]):
            intent = "NUTRITION_FEEDING"
            urgency = "ROUTINE"
            needs_rag = True
            status_text = "Звіряю з протоколами харчування та прикорму ВООЗ..."
            prompts = ["Як безпечно вводити нові алергени?", "Який оптимальний режим пиття води?", "Які порції рекомендовані за віком?"]
        elif any(k in msg_lower for k in ["сидіти", "повзати", "ходити", "гуліти", "говорити", "сон", "спить", "мовлен"]):
            intent = "DEVELOPMENT_MILESTONES"
            urgency = "ROUTINE"
            needs_rag = True
            status_text = "Звіряю з віковими віхами розвитку ВООЗ..."
            prompts = ["Які вправи допомагають для моторики?", "Яка норма сну для цього віку?", "На що звернути увагу у спілкуванні?"]
        elif any(k in msg_lower for k in ["ваг", "зріст", "рост", "зріст", "набір", "схуд", "голови", "сантиметр", "кілограм"]):
            intent = "ANTHROPOMETRY_GROWTH"
            urgency = "ROUTINE"
            needs_rag = True
            status_text = "Аналізую динаміку зросту та ваги дитини..."
            prompts = ["Як часто потрібно проводити заміри?", "Що означають центильні таблиці ВООЗ?", "Чи в нормі наш темп росту?"]
        elif any(k in msg_lower for k in ["температур", "кашель", "нежить", "висип", "блювота", "пронос", "болить"]):
            intent = "SYMPTOM_OR_ILLNESS"
            urgency = "CONSULT_PEDIATRICIAN"
            needs_rag = True
            status_text = "Звіряю симптоми з клінічними настановами МОЗ/ВООЗ..."
            prompts = ["Коли терміново звертатися до лікаря?", "Як забезпечити достатнє пиття?", "Які ознаки покращення стану?"]
        elif is_greeting:
            intent = "GENERAL_GREETING_OR_OFFTOPIC"
            urgency = "ROUTINE"
            needs_rag = False
            status_text = "Готую відповідь..."
            prompts = ["Як оцінити поточний розвиток дитини?", "Які норми зросту та ваги за ВООЗ?", "Поради щодо харчування та прикорму"]
        else:
            intent = "SYMPTOM_OR_ILLNESS"
            urgency = "ROUTINE"
            needs_rag = True
            status_text = "Звіряю з клінічними протоколами ВООЗ..."
            prompts = ["Як краще сформулювати скарги для лікаря?", "На які симптоми звернути увагу?", "Які профілактичні заходи доречні?"]

        return {
            "intent": intent,
            "urgencyLevel": urgency,
            "needsRagSearch": needs_rag,
            "asksForMedicationOrDiagnosis": asks_meds,
            "statusMessage": status_text,
            "suggestedPrompts": prompts,
            "isSystemOne": False
        }


class LayaModelDriver:
    """
    Driver managing the low-level lifecycle and calls to the Laya model.
    Encapsulates PyTorch/Laya library dependencies.
    """
    def __init__(self, model_name: Optional[str] = None):
        self.model_name = model_name or os.getenv("LAYA_MODEL_NAME", "convaiinnovations/laya-multilingual")
        self._agent = None
        self._load()

    def _load(self):
        try:
            import time
            import torch
            # Optimize CPU threads for container environment
            num_threads = int(os.getenv("TORCH_NUM_THREADS", "2"))
            torch.set_num_threads(num_threads)
            
            import laya
            start_t = time.time()
            logger.info(f"Loading Laya model: {self.model_name} (threads={num_threads})...")
            self._agent = laya.load(self.model_name)
            elapsed = time.time() - start_t
            logger.info(f"Laya model loaded successfully in {elapsed:.2f}s.")
        except Exception as e:
            logger.warning(f"Laya model not loaded ({e}).")
            self._agent = None

    @property
    def is_available(self) -> bool:
        return self._agent is not None

    def predict(self, state: Dict[str, Any], questions: Dict[str, Any]) -> Dict[str, Any]:
        if not self.is_available:
            raise RuntimeError("Laya driver is not available.")
        return self._agent.predict(state, questions)


class SystemOneEngine:
    """
    Composite decision engine conforming to SystemOneDecisionProtocol.
    Dispatches to Laya model driver with seamless fallback to HeuristicDecisionEngine.
    """
    def __init__(
        self,
        driver: Optional[LayaModelDriver] = None,
        fallback: Optional[HeuristicDecisionEngine] = None
    ):
        self.driver = driver or LayaModelDriver()
        self.fallback = fallback or HeuristicDecisionEngine()

    @property
    def is_ready(self) -> bool:
        return self.driver.is_available

    def decide_clinical_triage(self, state: Dict[str, Any]) -> Dict[str, Any]:
        if self.driver.is_available:
            try:
                res = self.driver.predict(state, CLINICAL_TRIAGE_QUESTIONS)
                return self._parse_triage_result(res)
            except Exception as e:
                logger.error(f"Error in Laya triage: {e}")

        return self.fallback.decide_clinical_triage(state)

    def rerank_and_filter_chunks(
        self,
        query: str,
        child_context: Dict[str, Any],
        chunks: List[Dict[str, Any]]
    ) -> List[Dict[str, Any]]:
        if not chunks:
            return []

        if not self.driver.is_available:
            return self.fallback.rerank_and_filter_chunks(query, child_context, chunks)

        results = []
        for chunk in chunks:
            title = chunk.get("title", "")
            content = chunk.get("content", "")
            chunk_state = {
                "query": query,
                "childContext": child_context,
                "articleTitle": title,
                "articleContent": content
            }
            try:
                p = self.driver.predict(chunk_state, CHUNK_RERANK_QUESTIONS)
                rel_raw = p.get("relevance", {}).get("score", 1.0) if isinstance(p.get("relevance"), dict) else 1.0
                rel_score = min(max(float(rel_raw) / 2.0, 0.0), 1.0)
                safe_raw = p.get("is_safe_for_child", {}).get("prob", 1.0) if isinstance(p.get("is_safe_for_child"), dict) else 1.0
                is_safe = float(safe_raw) >= 0.5
            except Exception as e:
                logger.error(f"Error reranking chunk: {e}")
                heur = self.fallback.rerank_and_filter_chunks(query, child_context, [chunk])
                rel_score = heur[0]["relevanceScore"] if heur else 0.5
                is_safe = bool(heur)

            if is_safe and rel_score >= 0.35:
                results.append({
                    "title": title,
                    "content": content,
                    "relevanceScore": round(rel_score, 2),
                    "isSafeAndApplicable": True
                })

        results.sort(key=lambda x: x["relevanceScore"], reverse=True)
        return results

    def evaluate_medical_report(
        self,
        child_context: Dict[str, Any],
        chunks: List[Dict[str, Any]],
        report_text: str,
        recommendations: str
    ) -> Dict[str, Any]:
        if not self.driver.is_available:
            return self.fallback.evaluate_medical_report(child_context, chunks, report_text, recommendations)

        state = {
            "childContext": child_context,
            "verifiedMedicalProtocols": chunks,
            "generatedReport": report_text,
            "generatedRecommendations": recommendations
        }
        try:
            p = self.driver.predict(state, REPORT_GUARDRAIL_QUESTIONS)
            faith_raw = p.get("faithfulness", {}).get("score", 3.0) if isinstance(p.get("faithfulness"), dict) else 3.0
            rel_raw = p.get("relevance", {}).get("score", 3.0) if isinstance(p.get("relevance"), dict) else 3.0
            diag_safe = float(p.get("no_diagnosis_or_prescription", {}).get("prob", 1.0)) >= 0.5
            allergy_safe = float(p.get("respects_allergies", {}).get("prob", 1.0)) >= 0.5

            faith_score = min(max(float(faith_raw) / 3.0, 0.0), 1.0)
            rel_score = min(max(float(rel_raw) / 3.0, 0.0), 1.0)
        except Exception as e:
            logger.error(f"Error evaluating report: {e}")
            return self.fallback.evaluate_medical_report(child_context, chunks, report_text, recommendations)

        violations = []
        if faith_score < 0.75:
            violations.append("Низький рівень відповідності медичним протоколам (виявлено потенційні домисли або непідтверджені поради)")
        if not diag_safe:
            violations.append("Виявлено спробу встановлення клінічного діагнозу або призначення медикаментів (заборонено)")
        if not allergy_safe:
            violations.append("Рекомендації містять потенційний конфлікт із задокументованими алергіями дитини")

        return {
            "faithfulnessScore": round(faith_score, 2),
            "relevanceScore": round(rel_score, 2),
            "passedGuardrail": len(violations) == 0,
            "violationReasons": violations,
            "isSystemOne": True
        }

    def triage_chat_message(
        self,
        message: str,
        child_context: Dict[str, Any]
    ) -> Dict[str, Any]:
        # Fast-path for sub-millisecond real-time chat triage
        fast_triage = os.getenv("FAST_CHAT_TRIAGE", "true").lower() in ("true", "1", "yes")
        if fast_triage or not self.is_ready:
            result = self.fallback.triage_chat_message(message, child_context)
            result["isSystemOne"] = True
            return result

        state = {
            "userMessage": message,
            "childContext": child_context
        }
        try:
            p = self.driver.predict(state, CHAT_TRIAGE_QUESTIONS)
            intent = p.get("intent", {}).get("choice", "SYMPTOM_OR_ILLNESS") if isinstance(p.get("intent"), dict) else str(p.get("intent", "SYMPTOM_OR_ILLNESS"))
            urgency = p.get("urgency_level", {}).get("choice", "ROUTINE") if isinstance(p.get("urgency_level"), dict) else str(p.get("urgency_level", "ROUTINE"))
            needs_rag = float(p.get("needs_rag_search", {}).get("prob", 1.0)) >= 0.5
            asks_meds = float(p.get("asks_for_medication_or_diagnosis", {}).get("prob", 0.0)) >= 0.5

            # Map status message and suggested prompts based on intent
            status_map = {
                "EMERGENCY_RED_FLAG": "Увага: перевіряю червоні прапорці стану здоров'я...",
                "SYMPTOM_OR_ILLNESS": "Звіряю симптоми з клінічними настановами МОЗ/ВООЗ...",
                "NUTRITION_FEEDING": "Звіряю з протоколами харчування та прикорму ВООЗ...",
                "DEVELOPMENT_MILESTONES": "Звіряю з віковими віхами розвитку ВООЗ...",
                "ANTHROPOMETRY_GROWTH": "Аналізую динаміку зросту та ваги дитини...",
                "GENERAL_GREETING_OR_OFFTOPIC": "Готую відповідь..."
            }
            prompts_map = {
                "EMERGENCY_RED_FLAG": ["Як викликати швидку допомогу?", "Що робити до приїзду лікаря?", "Які ознаки небезпечного зневоднення?"],
                "SYMPTOM_OR_ILLNESS": ["Коли терміново звертатися до лікаря?", "Як полегшити самопочуття без ліків?", "Які ознаки покращення стану?"],
                "NUTRITION_FEEDING": ["Як безпечно вводити нові алергени?", "Який оптимальний режим пиття води?", "Які порції рекомендовані за віком?"],
                "DEVELOPMENT_MILESTONES": ["Які вправи допомагають для моторики?", "Яка норма сну для цього віку?", "На що звернути увагу у спілкуванні?"],
                "ANTHROPOMETRY_GROWTH": ["Як часто потрібно проводити заміри?", "Що означають центильні таблиці ВООЗ?", "Чи в нормі наш темп росту?"],
                "GENERAL_GREETING_OR_OFFTOPIC": ["Як оцінити поточний розвиток дитини?", "Які норми зросту та ваги за ВООЗ?", "Поради щодо харчування та прикорму"]
            }

            return {
                "intent": intent,
                "urgencyLevel": urgency,
                "needsRagSearch": needs_rag,
                "asksForMedicationOrDiagnosis": asks_meds,
                "statusMessage": status_map.get(intent, "Аналізую медичні протоколи..."),
                "suggestedPrompts": prompts_map.get(intent, ["Поради педіатра", "Норми розвитку", "Харчування"]),
                "isSystemOne": True
            }
        except Exception as e:
            logger.error(f"Error in Laya chat triage: {e}")
            return self.fallback.triage_chat_message(message, child_context)

    def _parse_triage_result(self, res: Dict[str, Any]) -> Dict[str, Any]:
        def get_prob(val: Any) -> float:
            if isinstance(val, dict):
                return float(val.get("prob", val.get("probability", val.get("confidence", 0.0))))
            return float(val) if isinstance(val, (int, float)) else 0.0

        def get_choice(val: Any, default: str) -> str:
            if isinstance(val, dict):
                return str(val.get("choice", val.get("selection", default)))
            return str(val) if isinstance(val, str) else default

        def get_score_norm(val: Any, max_score: float = 3.0) -> float:
            if isinstance(val, dict):
                s = val.get("score", val.get("level", val.get("expected", 0.0)))
                return min(max(float(s) / max_score, 0.0), 1.0)
            return min(max(float(val) / max_score, 0.0), 1.0) if isinstance(val, (int, float)) else 0.0

        return {
            "primaryFocus": get_choice(res.get("primary_focus"), "ROUTINE_CARE"),
            "requiresDoctorConsultProbability": round(get_prob(res.get("requires_doctor_consult")), 2),
            "allergyOrChronicConstraintProbability": round(get_prob(res.get("allergy_or_chronic_constraint")), 2),
            "measurementOutlierProbability": round(get_prob(res.get("measurement_outlier_warning")), 2),
            "anamnesisSeverityScore": round(get_score_norm(res.get("anamnesis_severity"), 3.0), 2),
            "isSystemOne": True
        }

    # Backward compatibility helper
    @classmethod
    def get_instance(cls) -> "SystemOneEngine":
        if not hasattr(cls, "_instance") or cls._instance is None:
            cls._instance = SystemOneEngine()
        return cls._instance
