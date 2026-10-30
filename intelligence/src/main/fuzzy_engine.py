import logging
from typing import Dict, Any, List, Optional
import numpy as np
import skfuzzy as fuzz
from skfuzzy import control as ctrl

logger = logging.getLogger(__name__)

# Static universe ranges defined at module level for computational performance
AGE_RANGE = np.arange(0, 73, 1)
Z_RANGE = np.arange(-4, 4.1, 0.1)
V_RANGE = np.arange(-1, 1.1, 0.1)
REG_RANGE = np.arange(0, 2, 1)
S_RANGE = np.arange(0, 101, 1)
U_RANGE = np.arange(0, 1.01, 0.01)

# Pre-computed membership definitions for static universes
AGE_MF = {
    'INFANT': fuzz.trapmf(AGE_RANGE, [0, 0, 6, 12]),
    'TODDLER': fuzz.trapmf(AGE_RANGE, [10, 18, 24, 36]),
    'PRESCHOOL': fuzz.trapmf(AGE_RANGE, [30, 48, 72, 72])
}

VELOCITY_MF = {
    'FALLING_FAST': fuzz.trapmf(V_RANGE, [-1, -1, -0.6, -0.4]),
    'FALLING': fuzz.trimf(V_RANGE, [-0.5, -0.3, 0]),
    'STABLE': fuzz.trimf(V_RANGE, [-0.1, 0, 0.1]),
    'RISING': fuzz.trimf(V_RANGE, [0, 0.3, 0.6])
}

REGRESSION_MF = {
    'FALSE': fuzz.trimf(REG_RANGE, [0, 0, 0]),
    'TRUE': fuzz.trimf(REG_RANGE, [1, 1, 1])
}

URGENCY_MF = {
    'LOW': fuzz.trapmf(U_RANGE, [0, 0, 0.2, 0.4]),
    'MEDIUM': fuzz.trapmf(U_RANGE, [0.3, 0.4, 0.6, 0.7]),
    'HIGH': fuzz.trapmf(U_RANGE, [0.6, 0.8, 1, 1])
}

TERM_TRANSLATION = {
    'VERY_LOW': 'Дуже низький', 'LOW': 'Низький', 'NORMAL': 'Норма',
    'HIGH': 'Високий', 'VERY_HIGH': 'Дуже високий',
    'CRITICAL': 'Критичний рівень', 'BELOW_NORMAL': 'Нижче норми',
    'MEDIUM': 'Середній',
    'FALLING_FAST': 'Стрімке падіння', 'FALLING': 'Уповільнення',
    'STABLE': 'Стабільно', 'RISING': 'Покращення',
    'TRUE': 'Виявлено', 'FALSE': 'Відсутній',
    'INFANT': 'Немовля', 'TODDLER': 'Раннє дитинство', 'PRESCHOOL': 'Дошкільник'
}

VAR_TRANSLATION = {
    'motorGrossScore': 'Груба моторика', 'motorFineScore': 'Дрібна моторика',
    'speechScore': 'Мовлення', 'cognitiveScore': 'Когнітивні навички',
    'socialScore': 'Соціальні навички', 'selfCareScore': 'Самообслуговування',
    'sensoryScore': 'Сенсорика', 'wfaZscore': 'Вага до віку',
    'hfaZscore': 'Ріст до віку', 'bmiZscore': 'ІМТ',
    'hcfaZscore': 'Окружність голови', 'wfhZscore': 'Вага до росту',
    'wfaVelocity': 'Динаміка ваги', 'hfaVelocity': 'Динаміка росту',
    'is_regression': 'Регрес розвитку',
    'ageMonths': 'Вікова група'
}

class FuzzyEngine:
    """
    Encapsulates the mathematical Fuzzy Inference System (FIS).
    Responsible for Antecedent/Consequent definitions, dynamic rule synthesis,
    defuzzification to urgencyScore, and linguistic interpretations.
    """

    @staticmethod
    def get_default_skill_thresholds(age_months: int) -> Dict[str, float]:
        if age_months <= 12:
            return {'critical_low': 15.0, 'critical_high': 25.0, 'below_low': 40.0, 'below_high': 55.0, 'normal_low': 70.0}
        elif age_months <= 24:
            return {'critical_low': 20.0, 'critical_high': 30.0, 'below_low': 45.0, 'below_high': 60.0, 'normal_low': 75.0}
        elif age_months <= 36:
            return {'critical_low': 25.0, 'critical_high': 35.0, 'below_low': 50.0, 'below_high': 65.0, 'normal_low': 80.0}
        else:
            return {'critical_low': 30.0, 'critical_high': 40.0, 'below_low': 55.0, 'below_high': 70.0, 'normal_low': 85.0}

    def compute_fuzzy_inference(self, request_data: Dict[str, Any]) -> Dict[str, Any]:
        """
        Builds control system from antecedents and dynamic rules, then runs simulation.
        """
        age_months = ctrl.Antecedent(AGE_RANGE, 'ageMonths')
        wfa = ctrl.Antecedent(Z_RANGE, 'wfaZscore')
        hfa = ctrl.Antecedent(Z_RANGE, 'hfaZscore')
        bmi = ctrl.Antecedent(Z_RANGE, 'bmiZscore')
        hcfa = ctrl.Antecedent(Z_RANGE, 'hcfaZscore')
        wfh = ctrl.Antecedent(Z_RANGE, 'wfhZscore')

        wfa_v = ctrl.Antecedent(V_RANGE, 'wfaVelocity')
        hfa_v = ctrl.Antecedent(V_RANGE, 'hfaVelocity')
        bmi_v = ctrl.Antecedent(V_RANGE, 'bmiVelocity')
        hcfa_v = ctrl.Antecedent(V_RANGE, 'hcfaVelocity')
        wfh_v = ctrl.Antecedent(V_RANGE, 'wfhVelocity')

        regression = ctrl.Antecedent(REG_RANGE, 'is_regression')

        motor_gross = ctrl.Antecedent(S_RANGE, 'motorGrossScore')
        motor_fine = ctrl.Antecedent(S_RANGE, 'motorFineScore')
        speech = ctrl.Antecedent(S_RANGE, 'speechScore')
        cognitive = ctrl.Antecedent(S_RANGE, 'cognitiveScore')
        social = ctrl.Antecedent(S_RANGE, 'socialScore')
        self_care = ctrl.Antecedent(S_RANGE, 'selfCareScore')
        sensory = ctrl.Antecedent(S_RANGE, 'sensoryScore')

        urgency = ctrl.Consequent(U_RANGE, 'urgency')

        # Membership Functions using pre-computed definitions
        for term, mf in AGE_MF.items():
            age_months[term] = mf

        norms = request_data.get("norms") or {}
        for var_name, var in [('wfaZscore', wfa), ('hfaZscore', hfa),
                              ('bmiZscore', bmi), ('hcfaZscore', hcfa),
                              ('wfhZscore', wfh)]:
            n = norms.get(var_name, {})
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

        for var in [wfa_v, hfa_v, bmi_v, hcfa_v, wfh_v]:
            for term, mf in VELOCITY_MF.items():
                var[term] = mf

        for term, mf in REGRESSION_MF.items():
            regression[term] = mf

        age_val = request_data.get("ageMonths", 12)
        default_thresh = self.get_default_skill_thresholds(age_val)

        for var_name, var in [('motorGrossScore', motor_gross),
                              ('motorFineScore', motor_fine),
                              ('speechScore', speech),
                              ('cognitiveScore', cognitive),
                              ('socialScore', social),
                              ('selfCareScore', self_care),
                              ('sensoryScore', sensory)]:
            skill_norms = norms.get(var_name, {})
            c_low = skill_norms.get('CRITICAL_LOW', default_thresh['critical_low'])
            c_high = skill_norms.get('CRITICAL_HIGH', default_thresh['critical_high'])
            b_low = skill_norms.get('BELOW_LOW', default_thresh['below_low'])
            b_high = skill_norms.get('BELOW_HIGH', default_thresh['below_high'])
            n_low = skill_norms.get('NORMAL_LOW', default_thresh['normal_low'])

            var['CRITICAL'] = fuzz.trapmf(var.universe, [0, 0, c_low, c_high])
            var['BELOW_NORMAL'] = fuzz.trapmf(var.universe, [c_high, b_low, b_high, n_low])
            var['NORMAL'] = fuzz.trapmf(var.universe, [b_high, n_low, 100, 100])

        for term, mf in URGENCY_MF.items():
            urgency[term] = mf

        vars_map = {
            'wfaZscore': wfa, 'hfaZscore': hfa, 'bmiZscore': bmi,
            'hcfaZscore': hcfa, 'wfhZscore': wfh,
            'motorGrossScore': motor_gross, 'motorFineScore': motor_fine,
            'speechScore': speech, 'cognitiveScore': cognitive,
            'socialScore': social, 'selfCareScore': self_care,
            'sensoryScore': sensory,
            'wfaVelocity': wfa_v, 'hfaVelocity': hfa_v, 'bmiVelocity': bmi_v,
            'hcfaVelocity': hcfa_v, 'wfhVelocity': wfh_v,
            'is_regression': regression,
            'ageMonths': age_months
        }

        # Build Rules dynamically
        rules_list = []
        regressions_dict = request_data.get("regressions") or {}
        has_reg = any(regressions_dict.values()) if isinstance(regressions_dict, dict) else False

        for r in request_data.get("rules", []):
            try:
                cond_objs = []
                skip_rule = False
                for c in r.get('conditions', []):
                    var_name = c['variable']
                    val = 1.0 if var_name == 'is_regression' and has_reg else request_data.get(var_name)
                    if val is None:
                        skip_rule = True
                        break
                    var_obj = vars_map.get(var_name)
                    if var_obj:
                        cond_objs.append(var_obj[c['term']])

                if skip_rule or not cond_objs:
                    continue

                if_part = cond_objs[0]
                for next_cond in cond_objs[1:]:
                    if_part &= next_cond

                then_part = urgency[r['consequent']]
                rules_list.append(ctrl.Rule(if_part, then_part))
            except Exception as rule_err:
                logger.warning(f"Skipping rule: {r}, error: {rule_err}")

        if not rules_list:
            return self.compute_fallback(request_data)

        fis_ctrl = ctrl.ControlSystem(rules_list)
        fis_sim = ctrl.ControlSystemSimulation(fis_ctrl)

        inputs_provided = False
        active_antecedents = {a.label for a in fis_ctrl.antecedents}

        for key, var in vars_map.items():
            if key == 'is_regression':
                if key in active_antecedents:
                    fis_sim.input[key] = 1.0 if has_reg else 0.0
                    inputs_provided = True
                continue

            val = request_data.get(key)
            if val is not None and key in active_antecedents:
                fis_sim.input[key] = float(val)
                inputs_provided = True

        if not inputs_provided:
            return self.compute_fallback(request_data)

        fis_sim.compute()
        res_urgency = fis_sim.output['urgency']

        interpretations = []
        for key, var in vars_map.items():
            val = 1.0 if key == 'is_regression' and has_reg else request_data.get(key)
            if val is not None:
                best_term = None
                max_membership = 0.0
                for term in var.terms:
                    m = fuzz.interp_membership(var.universe, var[term].mf, float(val))
                    if m > max_membership:
                        max_membership = m
                        best_term = term

                if best_term and max_membership > 0.5:
                    label = VAR_TRANSLATION.get(key, key)
                    term_label = TERM_TRANSLATION.get(best_term, best_term)
                    if key == 'is_regression' and best_term == 'FALSE':
                        continue
                    interpretations.append(f"{label}: {term_label}")

        breakdown = {}
        for k, v in vars_map.items():
            val = 1.0 if k == 'is_regression' and has_reg else request_data.get(k)
            if val is not None:
                first_term = list(v.terms.keys())[0]
                m_val = round(float(fuzz.interp_membership(v.universe, v[first_term].mf, float(val))), 2)
                breakdown[k] = m_val

        return {
            "urgencyScore": round(float(res_urgency), 2),
            "isFallback": False,
            "breakdown": breakdown,
            "linguisticInterpretations": interpretations
        }

    def compute_fallback(self, request_data: Dict[str, Any]) -> Dict[str, Any]:
        breakdown = {}
        skill_score = 0.0
        skill_count = 0
        domains = [
            ("motor_gross", request_data.get("motorGrossScore")),
            ("motor_fine", request_data.get("motorFineScore")),
            ("speech", request_data.get("speechScore")),
            ("cognitive", request_data.get("cognitiveScore")),
            ("social", request_data.get("socialScore")),
            ("self_care", request_data.get("selfCareScore")),
            ("sensory", request_data.get("sensoryScore"))
        ]

        for name, val in domains:
            if val is not None:
                score = np.clip((80 - float(val)) / 50.0, 0, 1)
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
