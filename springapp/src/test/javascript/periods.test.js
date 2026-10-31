import { describe, it, expect, beforeEach, vi } from 'vitest';
import { PeriodsApi, PeriodsUI, addTip, removeTip, openEditModal, closeModal } from '../../main/resources/static/admin/js/periods.js';
import { ApiClient } from '../../main/resources/static/admin/js/api.js';

describe('Periods Module Unit Tests', () => {
    beforeEach(() => {
        vi.restoreAllMocks();

        document.body.innerHTML = `
            <table>
                <tbody id="periods-table-body"></tbody>
            </table>
            <div id="add-tips-list"></div>
            <div id="edit-modal" class="modal">
                <input id="edit-id" value="" />
                <input id="edit-code" value="" />
                <input id="edit-label" value="" />
                <input id="edit-minMonths" value="" />
                <input id="edit-maxMonths" value="" />
                <input id="edit-title" value="" />
                <textarea id="edit-subtitle"></textarea>
                <input id="edit-milestone-movement" value="" />
                <input id="edit-milestone-speech" value="" />
                <input id="edit-milestone-cognition" value="" />
                <div id="edit-tips-list"></div>
            </div>
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
    });

    describe('PeriodsApi', () => {
        it('should call ApiClient.get for life periods', async () => {
            // Given
            const spy = vi.spyOn(ApiClient, 'get').mockResolvedValue([{ id: 'p1', code: 'P0_3M' }]);

            // When
            const periods = await PeriodsApi.getPeriods();

            // Then
            expect(spy).toHaveBeenCalledWith('/admin/quiz/life-periods');
            expect(periods).toHaveLength(1);
        });

        it('should create new period via ApiClient.post', async () => {
            // Given
            const spy = vi.spyOn(ApiClient, 'post').mockResolvedValue({ id: 'p2' });
            const payload = { code: 'P3_6M', label: '3-6 months', minMonths: 3, maxMonths: 6 };

            // When
            await PeriodsApi.createPeriod(payload);

            // Then
            expect(spy).toHaveBeenCalledWith('/admin/quiz/life-periods', payload);
        });
    });

    describe('PeriodsUI & Dynamic Tip Management', () => {
        it('should dynamically append and remove tip input items', () => {
            // Given
            const container = document.getElementById('add-tips-list');

            // When
            addTip('add-tips-list', 'Корисна порада 1');

            // Then
            expect(container.children.length).toBe(1);
            expect(container.querySelector('.tip-input').value).toBe('Корисна порада 1');

            // When removing tip
            const removeBtn = container.querySelector('.remove-tip-btn');
            removeTip(removeBtn);

            // Then
            expect(container.children.length).toBe(0);
        });

        it('should populate edit modal and parse JSON description correctly', () => {
            // Given
            const mockPeriod = {
                id: 'p123',
                code: 'P0_3M',
                label: '0-3 міс',
                minMonths: 0,
                maxMonths: 3,
                description: JSON.stringify({
                    ua: {
                        title: 'Час відкриттів',
                        subtitle: 'Опис підзаголовка',
                        milestones: { movement: 'Тримає голову', speech: 'Гуління', cognition: 'Стежить за іграшкою' },
                        tips: ['Порада A']
                    }
                })
            };
            PeriodsUI.periods = [mockPeriod];

            // When
            openEditModal('p123');

            // Then
            expect(document.getElementById('edit-code').value).toBe('P0_3M');
            expect(document.getElementById('edit-title').value).toBe('Час відкриттів');
            expect(document.getElementById('edit-milestone-movement').value).toBe('Тримає голову');
            expect(document.getElementById('edit-tips-list').children.length).toBe(1);
            expect(document.getElementById('edit-modal').classList.contains('open')).toBe(true);

            // When closing modal
            closeModal();
            expect(document.getElementById('edit-modal').classList.contains('open')).toBe(false);
        });

        it('should escape HTML in periods table rendering', () => {
            // Given
            const tbody = document.getElementById('periods-table-body');
            PeriodsUI.periods = [
                { id: '1', code: '<script>code</script>', label: '<b>Label</b>', minMonths: 0, maxMonths: 3 }
            ];

            // When
            PeriodsUI.renderTable();

            // Then
            expect(tbody.querySelector('script')).toBeNull();
            expect(tbody.textContent).toContain('<script>code</script>');
        });
    });
});
