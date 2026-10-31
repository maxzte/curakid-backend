import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ArticlesApi, ArticlesUI, setUploadMode, deleteArticle } from '../../main/resources/static/admin/js/articles.js';
import { ApiClient } from '../../main/resources/static/admin/js/api.js';

describe('Articles Module Unit Tests', () => {
    beforeEach(() => {
        vi.restoreAllMocks();

        document.body.innerHTML = `
            <div id="toggle-manual" class="active"></div>
            <div id="toggle-file"></div>
            <div id="manual-input-section" style="display: block;"></div>
            <div id="file-input-section" style="display: none;"></div>
            <textarea id="content" required></textarea>
            <input type="file" id="file" />

            <select id="category"></select>
            <table>
                <tbody id="articles-table-body"></tbody>
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
    });

    describe('ArticlesApi', () => {
        it('should get knowledge articles and categories via ApiClient', async () => {
            // Given
            const spyGet = vi.spyOn(ApiClient, 'get').mockResolvedValue([]);

            // When
            await ArticlesApi.getCategories();
            await ArticlesApi.getArticles();

            // Then
            expect(spyGet).toHaveBeenCalledWith('/admin/knowledge/categories');
            expect(spyGet).toHaveBeenCalledWith('/admin/knowledge');
        });

        it('should delete article via ApiClient.delete', async () => {
            // Given
            const spyDel = vi.spyOn(ApiClient, 'delete').mockResolvedValue(null);

            // When
            await ArticlesApi.deleteArticle('art-123');

            // Then
            expect(spyDel).toHaveBeenCalledWith('/admin/knowledge/art-123');
        });
    });

    describe('ArticlesUI.setUploadMode', () => {
        it('should switch UI elements visibility and required attributes when mode is manual vs file', () => {
            // Given & When (Switching to file)
            setUploadMode('file');

            // Then
            expect(ArticlesUI.uploadMode).toBe('file');
            expect(document.getElementById('manual-input-section').style.display).toBe('none');
            expect(document.getElementById('file-input-section').style.display).toBe('block');
            expect(document.getElementById('content').required).toBe(false);
            expect(document.getElementById('file').required).toBe(true);

            // When (Switching back to manual)
            setUploadMode('manual');

            // Then
            expect(ArticlesUI.uploadMode).toBe('manual');
            expect(document.getElementById('manual-input-section').style.display).toBe('block');
            expect(document.getElementById('file-input-section').style.display).toBe('none');
            expect(document.getElementById('content').required).toBe(true);
            expect(document.getElementById('file').required).toBe(false);
        });
    });

    describe('ArticlesUI Rendering & XSS Protection', () => {
        it('should render articles table with category badge and sanitize XSS payload', () => {
            // Given
            const tbody = document.getElementById('articles-table-body');
            const mockArticles = [
                { id: '1', title: '<script>alert("xss")</script>', category: { displayName: 'Харчування' } }
            ];

            // When
            ArticlesUI.renderArticlesTable(mockArticles);

            // Then
            expect(tbody.children.length).toBe(1);
            expect(tbody.querySelector('script')).toBeNull();
            expect(tbody.textContent).toContain('<script>alert("xss")</script>');
            expect(tbody.textContent).toContain('Харчування');
        });

        it('should delete article and show success toast when user confirms', async () => {
            // Given
            window.confirm = vi.fn().mockReturnValue(true);
            const spy = vi.spyOn(ApiClient, 'delete').mockResolvedValue(null);
            vi.spyOn(ApiClient, 'get').mockResolvedValue([]);

            // When
            await deleteArticle('art-999');

            // Then
            expect(spy).toHaveBeenCalledWith('/admin/knowledge/art-999');
            expect(window.AdminAuth.showToast).toHaveBeenCalledWith('Статтю видалено.', 'success');
        });
    });
});
