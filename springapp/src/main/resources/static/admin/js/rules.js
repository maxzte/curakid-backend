/**
 * Fuzzy Rules Domain & UI Controller
 */
import { ApiClient } from './api.js';

// --- API Service Layer ---
export const RulesApi = {
    getRules: () => ApiClient.get('/admin/fuzzy/rules'),
    saveRules: (rulesToSave) => ApiClient.put('/admin/fuzzy/rules', { rules: rulesToSave })
};

// --- UI / Render Layer ---
export const RulesUI = {
    rules: [],
    variables: [
        'ageMonths', 'wfaZscore', 'hfaZscore', 'bmiZscore', 'hcfaZscore', 'wfhZscore',
        'motorGrossScore', 'motorFineScore', 'speechScore',
        'cognitiveScore', 'socialScore', 'selfCareScore', 'sensoryScore',
        'wfaVelocity', 'hfaVelocity', 'bmiVelocity', 'hcfaVelocity', 'wfhVelocity',
        'is_regression'
    ],
    domains: ['GENERAL', 'MOTOR', 'SPEECH', 'COGNITIVE', 'SOCIAL', 'PHYSICAL'],
    terms: {
        age: ['INFANT', 'TODDLER', 'PRESCHOOL'],
        z: ['VERY_LOW', 'LOW', 'NORMAL', 'HIGH', 'VERY_HIGH'],
        skill: ['CRITICAL', 'BELOW_NORMAL', 'NORMAL'],
        velocity: ['FALLING_FAST', 'FALLING', 'STABLE', 'RISING'],
        binary: ['TRUE', 'FALSE']
    },

    getTermsForVariable(v) {
        if (!v) return this.terms.z;
        if (v === 'ageMonths') return this.terms.age;
        if (v === 'is_regression') return this.terms.binary;
        if (v.includes('Velocity')) return this.terms.velocity;
        if (v.includes('Score')) return this.terms.skill;
        return this.terms.z;
    },

    renderRules() {
        const container = document.getElementById('rules-container');
        if (!container) return;
        container.innerHTML = '';

        this.rules.forEach((rule, index) => {
            const conditions = rule.conditions || [];
            const usedVariablesInRule = conditions.map(c => c.variable);
            const div = document.createElement('div');
            div.className = 'rule-item';
            div.innerHTML = `
                <div class="rule-header flex justify-between">
                    <select onchange="RulesUI.rules[${index}].domain = this.value" style="background: var(--bg-color); color: var(--primary); border: none; padding: 0.25rem 0.75rem; border-radius: 20px; font-size: 0.75rem; font-weight: 700; cursor: pointer; outline: none;">
                        ${this.domains.map(d => `<option value="${d}" ${rule.domain === d ? 'selected' : ''}>${d}</option>`).join('')}
                    </select>
                    <i class="fas fa-trash trash-btn" onclick="removeRule(${index})"></i>
                </div>
                <div class="logic-row" style="display: flex; align-items: center; gap: 1rem; flex-wrap: wrap; margin-bottom: 1rem;">
                    <span class="logic-keyword" style="width: 50px; color: var(--primary); font-weight: 700; text-transform: lowercase;">якщо</span>
                    ${conditions.map((c, ci) => `
                        <div style="display:inline-flex; align-items:center; gap:0.5rem; background: #f9fafb; padding: 0.5rem 0.75rem; border-radius: 10px; border: 1px solid #f3f4f6;">
                            <select onchange="updateCondition(${index}, ${ci}, 'variable', this.value)" style="border: none; background: transparent; font-weight: 500; cursor: pointer;">
                                ${this.variables.map(v => {
                                    const isUsed = usedVariablesInRule.includes(v) && v !== c.variable;
                                    return `<option value="${v}" ${c.variable === v ? 'selected' : ''} ${isUsed ? 'disabled' : ''}>${v}</option>`;
                                }).join('')}
                            </select>
                            <span class="logic-keyword" style="color: var(--text-muted); font-weight: 400;">є</span>
                            <select onchange="updateCondition(${index}, ${ci}, 'term', this.value)" style="border: none; background: transparent; font-weight: 600; color: var(--primary); cursor: pointer;">
                                ${this.getTermsForVariable(c.variable).map(t =>
                                    `<option value="${t}" ${c.term === t ? 'selected' : ''}>${t}</option>`
                                ).join('')}
                            </select>
                            ${ci < conditions.length - 1 ? '<span class="logic-keyword" style="margin: 0 0.25rem; font-weight: 700;">та</span>' : ''}
                            ${conditions.length > 1 ? `<i class="fas fa-times" style="font-size: 0.7rem; color: var(--error); cursor: pointer; margin-left: 0.5rem;" onclick="removeCondition(${index}, ${ci})"></i>` : ''}
                        </div>
                    `).join('')}
                    ${conditions.length < this.variables.length ? `
                        <i class="fas fa-plus-circle" style="cursor:pointer; color:var(--primary); font-size: 1.25rem; transition: transform 0.2s;" onmouseover="this.style.transform='scale(1.1)'" onmouseout="this.style.transform='scale(1)'" onclick="addCondition(${index})"></i>
                    ` : ''}
                </div>
                <div class="logic-row" style="margin-top: 1.5rem; padding-top: 1.5rem; border-top: 2px solid #f9fafb; display: flex; align-items: center; gap: 1rem;">
                    <span class="logic-keyword" style="width: 50px; color: #4338CA; font-weight: 700; text-transform: lowercase;">тоді</span>
                    <span style="color: var(--text-muted);">критичність є</span>
                    <select onchange="RulesUI.rules[${index}].consequent = this.value" style="background: #EEF2FF; border: 1px solid #C7D2FE; padding: 0.4rem 1rem; border-radius: 8px; font-weight: 700; color: #4338CA; cursor: pointer;">
                        <option value="LOW" ${rule.consequent === 'LOW' ? 'selected' : ''}>НИЗЬКА</option>
                        <option value="MEDIUM" ${rule.consequent === 'MEDIUM' ? 'selected' : ''}>СЕРЕДНЯ</option>
                        <option value="HIGH" ${rule.consequent === 'HIGH' ? 'selected' : ''}>ВИСОКА</option>
                    </select>
                </div>
            `;
            container.appendChild(div);
        });
    },

    openJsonEditor() {
        const cleanRules = this.rules.map(({ id, createdAt, updatedAt, ...rest }) => rest);
        const editor = document.getElementById('rules-json');
        if (editor) editor.value = JSON.stringify(cleanRules, null, 2);
        document.getElementById('json-modal')?.classList.add('open');
    },

    closeJsonEditor() {
        document.getElementById('json-modal')?.classList.remove('open');
    }
};

export function addNewRule() {
    RulesUI.rules.push({
        domain: 'GENERAL',
        conditions: [{ variable: 'wfaZscore', term: 'NORMAL' }],
        consequent: 'LOW',
        isActive: true
    });
    RulesUI.renderRules();
}

export function addCondition(ruleIndex) {
    const used = RulesUI.rules[ruleIndex].conditions.map(c => c.variable);
    const nextVar = RulesUI.variables.find(v => !used.includes(v));
    if (nextVar) {
        RulesUI.rules[ruleIndex].conditions.push({ variable: nextVar, term: nextVar.includes('Score') ? 'NORMAL' : 'NORMAL' });
        RulesUI.renderRules();
    }
}

export function removeCondition(ri, ci) {
    RulesUI.rules[ri].conditions.splice(ci, 1);
    RulesUI.renderRules();
}

export function updateCondition(ri, ci, field, val) {
    RulesUI.rules[ri].conditions[ci][field] = val;
    RulesUI.renderRules();
}

export function removeRule(index) {
    RulesUI.rules.splice(index, 1);
    RulesUI.renderRules();
}

export async function saveAllRules() {
    try {
        const rulesToSave = RulesUI.rules.map(r => ({
            domain: r.domain || 'GENERAL',
            conditions: r.conditions || [],
            consequent: r.consequent || 'MEDIUM',
            isActive: r.isActive !== undefined ? r.isActive : (r.active !== undefined ? r.active : true)
        }));

        await RulesApi.saveRules(rulesToSave);
        if (typeof window !== 'undefined' && window.AdminAuth && window.AdminAuth.showToast) {
            window.AdminAuth.showToast("Базу знань оновлено успішно!", "success");
        }
    } catch (e) {
        // Handled
    }
}

export function openJsonEditor() {
    RulesUI.openJsonEditor();
}

export function closeJsonEditor() {
    RulesUI.closeJsonEditor();
}

export async function applyJsonRules() {
    try {
        const val = document.getElementById('rules-json').value;
        const newRules = JSON.parse(val);
        if (!Array.isArray(newRules)) throw new Error("Rules must be an array");

        RulesUI.rules = newRules;
        RulesUI.renderRules();
        RulesUI.closeJsonEditor();

        await saveAllRules();
    } catch (e) {
        if (typeof window !== 'undefined' && window.AdminAuth && window.AdminAuth.showToast) {
            window.AdminAuth.showToast("Помилка JSON: " + e.message, "error");
        }
    }
}

export async function loadRulesData() {
    try {
        RulesUI.rules = await RulesApi.getRules();
        RulesUI.renderRules();
    } catch (e) {
        // Handled
    }
}

if (typeof window !== 'undefined') {
    window.RulesApi = RulesApi;
    window.RulesUI = RulesUI;
    window.addNewRule = addNewRule;
    window.addCondition = addCondition;
    window.removeCondition = removeCondition;
    window.updateCondition = updateCondition;
    window.removeRule = removeRule;
    window.saveAllRules = saveAllRules;
    window.openJsonEditor = openJsonEditor;
    window.closeJsonEditor = closeJsonEditor;
    window.applyJsonRules = applyJsonRules;

    document.addEventListener('DOMContentLoaded', async () => {
        await loadRulesData();
    });
}
