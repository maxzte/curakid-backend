import { describe, it, expect, beforeEach, vi } from 'vitest';
import { VaccinesApi, VaccinesUI, openSyncModal, triggerBackfill, deleteVaccine } from '../../main/resources/static/admin/js/vaccines.js';
import { ApiClient } from '../../main/resources/static/admin/js/api.js';

describe('Vaccines Module Unit Tests', () => {
    beforeEach(() => {
        vi.restoreAllMocks();

        document.body.innerHTML = `
            <table>
                <tbody id="vaccines-table-body"></tbody>
            </table>
            <div id="sync-modal" style="display: none;">
                <div id="sync-loading"></div>
                <div id="sync-content">
                    <table><tbody id="sync-new-body"></tbody></table>
                    <table><tbody id="sync-changed-body"></tbody></table>
                    <table><tbody id="sync-missing-body"></tbody></table>
                </div>
            </div>
            <form id="vaccine-form">
                <input id="vaccine-id" value="" />
                <input id="name" value="" />
                <input id="disease" value="" />
                <input id="doseNumber" value="1" />
                <input id="totalDoses" value="1" />
                <input id="type" value="VACCINATION" />
                <input id="scheduledAgeMonths" value="" />
                <input id="scheduledAgeDays" value="" />
                <textarea id="notes"></textarea>
                <span id="form-title"></span>
                <button id="submit-btn"></button>
                <button id="cancel-btn" style="display: none;"></button>
            </form>
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
    });

    describe('VaccinesApi', () => {
        it('should fetch vaccines via ApiClient', async () => {
            // Given
            const spy = vi.spyOn(ApiClient, 'get').mockResolvedValue([{ id: '1', name: 'BCG' }]);

            // When
            const result = await VaccinesApi.getVaccines();

            // Then
            expect(spy).toHaveBeenCalledWith('/admin/vaccines');
            expect(result).toHaveLength(1);
        });

        it('should send sync apply payload to backend', async () => {
            // Given
            const spy = vi.spyOn(ApiClient, 'post').mockResolvedValue({ success: true });
            const payload = { toAdd: [], toUpdate: [] };

            // When
            await VaccinesApi.applySync(payload);

            // Then
            expect(spy).toHaveBeenCalledWith('/admin/vaccines/sync/apply', payload);
        });
    });

    describe('VaccinesUI.renderVaccines', () => {
        it('should sort vaccines by scheduled age and render sanitized table rows', () => {
            // Given
            const tbody = document.getElementById('vaccines-table-body');
            const mockVaccines = [
                { id: '1', name: 'Hepatitis B', disease: '<script>xss</script>', scheduledAgeMonths: 2, doseNumber: 1, totalDoses: 3, type: 'VACCINATION' },
                { id: '2', name: 'BCG', disease: 'Tuberculosis', scheduledAgeDays: 3, doseNumber: 1, totalDoses: 1, type: 'VACCINATION' }
            ];

            // When
            VaccinesUI.renderVaccines(mockVaccines);

            // Then
            expect(tbody.children.length).toBe(2);
            // BCG (3 days) comes before Hepatitis B (2 months = ~60 days)
            expect(tbody.children[0].innerHTML).toContain('BCG');
            expect(tbody.children[1].innerHTML).toContain('Hepatitis B');
            // XSS escaping check
            expect(tbody.querySelector('script')).toBeNull();
            expect(tbody.textContent).toContain('<script>xss</script>');
        });

        it('should format age correctly in formatAge', () => {
            // Given & When & Then
            expect(VaccinesUI.formatAge(2, 5)).toBe('2 міс. 5 дн.');
            expect(VaccinesUI.formatAge(0, 0)).toBe('0 дн.');
            expect(VaccinesUI.formatAge(3, null)).toBe('3 міс.');
        });
    });

    describe('Sync Modal & Backfill Actions', () => {
        it('should populate sync preview modal with new, changed, and missing vaccines', async () => {
            // Given
            const mockSyncData = {
                newVaccines: [{ name: 'New Vac', disease: 'Polio', doseNumber: 1, totalDoses: 1, type: 'VACCINATION', scheduledAgeMonths: 3 }],
                changedVaccines: [{ current: { scheduledAgeMonths: 2 }, proposed: { name: 'Changed Vac', disease: 'Measles', doseNumber: 1, totalDoses: 2, scheduledAgeMonths: 4 } }],
                missingVaccines: []
            };
            vi.spyOn(ApiClient, 'post').mockResolvedValue(mockSyncData);

            // When
            await openSyncModal();

            // Then
            const newBody = document.getElementById('sync-new-body');
            const changedBody = document.getElementById('sync-changed-body');
            const missingBody = document.getElementById('sync-missing-body');

            expect(newBody.innerHTML).toContain('New Vac');
            expect(changedBody.innerHTML).toContain('Changed Vac');
            expect(missingBody.innerHTML).toContain('Усі вакцини знайдено на сайті');
        });

        it('should call triggerBackfill and show success toast with statistics', async () => {
            // Given
            window.confirm = vi.fn().mockReturnValue(true);
            const spy = vi.spyOn(ApiClient, 'post').mockResolvedValue({
                totalChildren: 10,
                createdEventsCount: 20,
                skippedEventsCount: 5,
                plannedEventsCount: 15
            });

            // When
            await triggerBackfill();

            // Then
            expect(spy).toHaveBeenCalledWith('/admin/vaccines/sync/backfill');
            expect(window.AdminAuth.showToast).toHaveBeenCalledWith(
                expect.stringContaining('Опрацьовано дітей: 10'),
                'success'
            );
        });

        it('should delete vaccine when confirmed', async () => {
            // Given
            window.confirm = vi.fn().mockReturnValue(true);
            const spy = vi.spyOn(ApiClient, 'delete').mockResolvedValue(null);
            vi.spyOn(ApiClient, 'get').mockResolvedValue([]);

            // When
            await deleteVaccine('vac-123');

            // Then
            expect(spy).toHaveBeenCalledWith('/admin/vaccines/vac-123');
            expect(window.AdminAuth.showToast).toHaveBeenCalledWith('Видалено', 'success');
        });
    });
});
