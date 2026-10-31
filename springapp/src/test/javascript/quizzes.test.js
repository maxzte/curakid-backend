/**
 * @vitest-environment jsdom
 */
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { QuizzesApi, QuizzesUI, addQuestion, addOption, deleteQuestion } from '../../main/resources/static/admin/js/quizzes.js';
import { ApiClient } from '../../main/resources/static/admin/js/api.js';

describe('Quizzes Module Unit Tests', () => {
    beforeEach(() => {
        document.body.innerHTML = `
            <div id="periods-container"></div>
            <input id="q-text-p1" value="Чи сидить малюк?" />
            <select id="q-domain-p1"><option value="MOTOR_GROSS" selected>Груба моторика</option></select>
            <input id="q-weight-p1" value="1.0" />
            <select id="q-prereq-p1"><option value="">Без передумови</option></select>
            <div id="q-options-container-p1"></div>

            <input id="o-text-q1" value="Так" />
            <input id="o-value-q1" value="100.0" />
            <input id="o-order-q1" value="1" />
        `;

        window.AdminAuth = {
            escapeHtml: (str) => String(str ?? '')
                .replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;')
                .replace(/"/g, '&quot;')
                .replace(/'/g, '&#039;'),
            showToast: vi.fn()
        };

        window.confirm = vi.fn().mockReturnValue(true);
        vi.restoreAllMocks();
    });

    describe('QuizzesApi', () => {
        it('should fetch life periods via ApiClient', async () => {
            // Given
            const spy = vi.spyOn(ApiClient, 'get').mockResolvedValue([{ id: 'p1', label: '0-3 months' }]);

            // When
            const result = await QuizzesApi.getLifePeriods();

            // Then
            expect(spy).toHaveBeenCalledWith('/admin/quiz/life-periods');
            expect(result).toHaveLength(1);
        });

        it('should add question to period via ApiClient.post', async () => {
            // Given
            const spy = vi.spyOn(ApiClient, 'post').mockResolvedValue({ id: 'q1' });
            const payload = { text: 'Test?', domain: 'MOTOR_GROSS', weight: 1.0 };

            // When
            await QuizzesApi.addQuestion('p1', payload);

            // Then
            expect(spy).toHaveBeenCalledWith('/admin/quiz/life-periods/p1/questions', payload);
        });
    });

    describe('QuizzesUI Helpers & Data Normalization', () => {
        it('should normalize periods payload from various response structures', () => {
            // Given & When & Then
            expect(QuizzesUI.normalizePeriodsPayload([{ id: '1' }])).toHaveLength(1);
            expect(QuizzesUI.normalizePeriodsPayload({ items: [{ id: '2' }] })).toHaveLength(1);
            expect(QuizzesUI.normalizePeriodsPayload({ content: [{ id: '3' }] })).toHaveLength(1);
            expect(QuizzesUI.normalizePeriodsPayload(null)).toEqual([]);
        });

        it('should normalize period min/max months and questions array', () => {
            // Given
            const rawPeriod = { id: 'p1', min_months: 0, max_months: 3, quizQuestions: [{ id: 'q1' }] };

            // When
            const normalized = QuizzesUI.normalizePeriod(rawPeriod);

            // Then
            expect(normalized.minMonths).toBe(0);
            expect(normalized.maxMonths).toBe(3);
            expect(normalized.questions).toHaveLength(1);
        });
    });

    describe('Guard Clauses & Validation Actions', () => {
        it('should show error toast and abort if option value is out of 0..100 range', async () => {
            // Given
            document.getElementById('o-value-q1').value = '150.0'; // Invalid value > 100
            const spy = vi.spyOn(ApiClient, 'post');

            // When
            await addOption('q1');

            // Then
            expect(spy).not.toHaveBeenCalled();
            expect(window.AdminAuth.showToast).toHaveBeenCalledWith('Значення має бути числом від 0 до 100', 'error');
        });

        it('should show error toast if question text is empty', async () => {
            // Given
            document.getElementById('q-text-p1').value = '';
            const spy = vi.spyOn(ApiClient, 'post');

            // When
            await addQuestion('p1');

            // Then
            expect(spy).not.toHaveBeenCalled();
            expect(window.AdminAuth.showToast).toHaveBeenCalledWith('Текст запитання обов’язковий', 'error');
        });

        it('should call deleteQuestion API and reload data when confirmed', async () => {
            // Given
            window.confirm = vi.fn().mockReturnValue(true);
            const spy = vi.spyOn(ApiClient, 'delete').mockResolvedValue(null);
            vi.spyOn(ApiClient, 'get').mockResolvedValue([]);

            // When
            await deleteQuestion('q123');

            // Then
            expect(spy).toHaveBeenCalledWith('/admin/quiz/questions/q123');
            expect(window.AdminAuth.showToast).toHaveBeenCalledWith('Запитання видалено', 'success');
        });
    });
});
