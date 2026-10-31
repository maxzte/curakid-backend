import { describe, it, expect, beforeEach, vi } from 'vitest';
import { RulesApi, RulesUI, addNewRule, addCondition, removeCondition, updateCondition, removeRule, saveAllRules, applyJsonRules } from '../../main/resources/static/admin/js/rules.js';
import { ApiClient } from '../../main/resources/static/admin/js/api.js';

describe('Rules Module Unit Tests (Fuzzy Logic)', () => {
    beforeEach(() => {
        document.body.innerHTML = `
            <div id="rules-container"></div>
            <div id="json-modal" class="modal">
                <textarea id="rules-json"></textarea>
            </div>
        `;

        window.AdminAuth = {
            showToast: vi.fn()
        };

        RulesUI.rules = [];
        vi.restoreAllMocks();
    });

    describe('RulesApi', () => {
        it('should get rules via ApiClient.get', async () => {
            // Given
            const spy = vi.spyOn(ApiClient, 'get').mockResolvedValue([{ domain: 'GENERAL', conditions: [] }]);

            // When
            const result = await RulesApi.getRules();

            // Then
            expect(spy).toHaveBeenCalledWith('/admin/fuzzy/rules');
            expect(result).toHaveLength(1);
        });

        it('should save rules via ApiClient.put with wrapped object', async () => {
            // Given
            const spy = vi.spyOn(ApiClient, 'put').mockResolvedValue({ success: true });
            const rulesToSave = [{ domain: 'MOTOR', conditions: [], consequent: 'HIGH', isActive: true }];

            // When
            await RulesApi.saveRules(rulesToSave);

            // Then
            expect(spy).toHaveBeenCalledWith('/admin/fuzzy/rules', { rules: rulesToSave });
        });
    });

    describe('RulesUI Logic & Linguistic Terms', () => {
        it('should return correct linguistic terms per variable type', () => {
            // Given & When & Then
            expect(RulesUI.getTermsForVariable('ageMonths')).toEqual(['INFANT', 'TODDLER', 'PRESCHOOL']);
            expect(RulesUI.getTermsForVariable('is_regression')).toEqual(['TRUE', 'FALSE']);
            expect(RulesUI.getTermsForVariable('wfaVelocity')).toEqual(['FALLING_FAST', 'FALLING', 'STABLE', 'RISING']);
            expect(RulesUI.getTermsForVariable('speechScore')).toEqual(['CRITICAL', 'BELOW_NORMAL', 'NORMAL']);
            expect(RulesUI.getTermsForVariable('wfaZscore')).toEqual(['VERY_LOW', 'LOW', 'NORMAL', 'HIGH', 'VERY_HIGH']);
        });

        it('should mutate rules state when adding a rule or condition', () => {
            // Given
            RulesUI.rules = [];

            // When
            addNewRule();

            // Then
            expect(RulesUI.rules).toHaveLength(1);
            expect(RulesUI.rules[0].domain).toBe('GENERAL');
            expect(RulesUI.rules[0].conditions).toHaveLength(1);

            // When adding condition
            addCondition(0);

            // Then
            expect(RulesUI.rules[0].conditions).toHaveLength(2);
        });

        it('should update or remove condition properly', () => {
            // Given
            RulesUI.rules = [{
                domain: 'GENERAL',
                conditions: [
                    { variable: 'wfaZscore', term: 'NORMAL' },
                    { variable: 'hfaZscore', term: 'LOW' }
                ],
                consequent: 'MEDIUM'
            }];

            // When updating condition
            updateCondition(0, 0, 'term', 'VERY_LOW');
            expect(RulesUI.rules[0].conditions[0].term).toBe('VERY_LOW');

            // When removing condition
            removeCondition(0, 0);
            expect(RulesUI.rules[0].conditions).toHaveLength(1);
            expect(RulesUI.rules[0].conditions[0].variable).toBe('hfaZscore');
        });

        it('should remove entire rule by index', () => {
            // Given
            RulesUI.rules = [{ domain: 'GENERAL' }, { domain: 'MOTOR' }];

            // When
            removeRule(0);

            // Then
            expect(RulesUI.rules).toHaveLength(1);
            expect(RulesUI.rules[0].domain).toBe('MOTOR');
        });
    });

    describe('JSON Editor & Save Actions', () => {
        it('should parse valid JSON array and save rules via applyJsonRules', async () => {
            // Given
            const validJson = JSON.stringify([
                { domain: 'SPEECH', conditions: [{ variable: 'speechScore', term: 'CRITICAL' }], consequent: 'HIGH', isActive: true }
            ]);
            document.getElementById('rules-json').value = validJson;
            vi.spyOn(RulesApi, 'saveRules').mockResolvedValue({ success: true });

            // When
            await applyJsonRules();

            // Then
            expect(RulesUI.rules).toHaveLength(1);
            expect(RulesUI.rules[0].domain).toBe('SPEECH');
            expect(window.AdminAuth.showToast).toHaveBeenCalledWith("Базу знань оновлено успішно!", "success");
        });

        it('should show error toast if applied JSON is invalid', async () => {
            // Given
            document.getElementById('rules-json').value = '{ notAnArray: true }';

            // When
            await applyJsonRules();

            // Then
            expect(window.AdminAuth.showToast).toHaveBeenCalledWith(
                expect.stringContaining("Помилка JSON"),
                "error"
            );
        });
    });
});
