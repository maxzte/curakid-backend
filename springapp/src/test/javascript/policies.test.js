import { describe, it, expect, beforeEach, vi } from 'vitest';
import { PoliciesApi, PoliciesUI, activateDoc } from '../../main/resources/static/admin/js/policies.js';
import { ApiClient } from '../../main/resources/static/admin/js/api.js';

describe('Policies Module Unit Tests', () => {
    beforeEach(() => {
        document.body.innerHTML = `
            <table>
                <tbody id="policies-table-body"></tbody>
            </table>
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

    describe('PoliciesApi', () => {
        it('should call ApiClient.get for legal documents', async () => {
            // Given
            const spy = vi.spyOn(ApiClient, 'get').mockResolvedValue([]);

            // When
            await PoliciesApi.getDocuments();

            // Then
            expect(spy).toHaveBeenCalledWith('/admin/legal');
        });

        it('should activate legal document via ApiClient.post', async () => {
            // Given
            const spy = vi.spyOn(ApiClient, 'post').mockResolvedValue({ success: true });

            // When
            await PoliciesApi.activateDocument('doc-1');

            // Then
            expect(spy).toHaveBeenCalledWith('/admin/legal/doc-1/activate');
        });
    });

    describe('PoliciesUI Helpers & Rendering', () => {
        it('should map document types to Ukrainian labels in typeLabel', () => {
            // Given & When & Then
            expect(PoliciesUI.typeLabel('TERMS')).toBe('Умови використання');
            expect(PoliciesUI.typeLabel('PRIVACY_POLICY')).toBe('Політика конфіденційності');
            expect(PoliciesUI.typeLabel('OTHER')).toBe('OTHER');
        });

        it('should sort active documents first, then by published date descending', () => {
            // Given
            const tbody = document.getElementById('policies-table-body');
            const docs = [
                { id: '1', type: 'TERMS', version: '1.0', url: 'https://example.com/1', isActive: false, publishedAt: '2024-01-01' },
                { id: '2', type: 'PRIVACY_POLICY', version: '2.0', url: 'https://example.com/2', isActive: true, publishedAt: '2024-02-01' }
            ];

            // When
            PoliciesUI.renderTable(docs);

            // Then
            // Active doc (id 2) should be first
            expect(tbody.children[0].innerHTML).toContain('Політика конфіденційності');
            expect(tbody.children[0].innerHTML).toContain('2.0');
            expect(tbody.children[0].innerHTML).toContain('Активний');
            expect(tbody.children[1].innerHTML).toContain('Неактивний');
        });

        it('should activate document and display success toast when confirmed', async () => {
            // Given
            window.confirm = vi.fn().mockReturnValue(true);
            const spy = vi.spyOn(ApiClient, 'post').mockResolvedValue(null);
            vi.spyOn(ApiClient, 'get').mockResolvedValue([]);

            // When
            await activateDoc('doc-99');

            // Then
            expect(spy).toHaveBeenCalledWith('/admin/legal/doc-99/activate');
            expect(window.AdminAuth.showToast).toHaveBeenCalledWith('Документ активовано', 'success');
        });
    });
});
