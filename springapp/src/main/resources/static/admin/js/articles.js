/**
 * Knowledge Base Articles Domain & UI Controller
 */
import { ApiClient } from './api.js';

// --- API Service Layer ---
export const ArticlesApi = {
    getCategories: () => ApiClient.get('/admin/knowledge/categories'),
    getArticles: () => ApiClient.get('/admin/knowledge'),
    createArticle: (payload) => ApiClient.post('/admin/knowledge', payload),
    uploadArticleFile: (formData) => ApiClient.post('/admin/knowledge/upload', formData),
    createCategory: (payload) => ApiClient.post('/admin/knowledge/categories', payload),
    deleteArticle: (id) => ApiClient.delete(`/admin/knowledge/${id}`)
};

// --- UI / Render Layer ---
export const ArticlesUI = {
    uploadMode: 'manual',

    setUploadMode(mode) {
        this.uploadMode = mode;
        document.getElementById('toggle-manual')?.classList.toggle('active', mode === 'manual');
        document.getElementById('toggle-file')?.classList.toggle('active', mode === 'file');

        const manualSec = document.getElementById('manual-input-section');
        const fileSec = document.getElementById('file-input-section');
        if (manualSec) manualSec.style.display = mode === 'manual' ? 'block' : 'none';
        if (fileSec) fileSec.style.display = mode === 'file' ? 'block' : 'none';

        const contentEl = document.getElementById('content');
        const fileEl = document.getElementById('file');
        if (contentEl) contentEl.required = mode === 'manual';
        if (fileEl) fileEl.required = mode === 'file';
    },

    renderCategoriesSelect(categories) {
        const select = document.getElementById('category');
        if (!select) return;

        const escapeHtml = (str) => (typeof window !== 'undefined' && window.AdminAuth && window.AdminAuth.escapeHtml)
            ? window.AdminAuth.escapeHtml(str) : String(str ?? '');

        if (!categories || categories.length === 0) {
            select.innerHTML = '<option value="">Немає категорій - будь ласка, створіть спочатку</option>';
        } else {
            select.innerHTML = categories.map(c => `
                <option value="${c.id}">${escapeHtml(c.displayName)} (${escapeHtml(c.name)})</option>
            `).join('');
        }
    },

    renderArticlesTable(articles) {
        const tbody = document.getElementById('articles-table-body');
        if (!tbody) return;

        const escapeHtml = (str) => (typeof window !== 'undefined' && window.AdminAuth && window.AdminAuth.escapeHtml)
            ? window.AdminAuth.escapeHtml(str) : String(str ?? '');

        if (!articles || articles.length === 0) {
            tbody.innerHTML = '<tr><td colspan="3" style="text-align: center; color: var(--text-muted);">Статей не знайдено.</td></tr>';
            return;
        }

        tbody.innerHTML = articles.map(art => {
            const cat = art.category ? art.category.displayName : 'Без категорії';
            return `
            <tr>
                <td style="font-weight: 500;">${escapeHtml(art.title)}</td>
                <td><span class="badge">${escapeHtml(cat)}</span></td>
                <td>
                    <button class="delete-btn" onclick="deleteArticle('${art.id}')">Видалити</button>
                </td>
            </tr>
            `;
        }).join('');
    }
};

export async function setUploadMode(mode) {
    ArticlesUI.setUploadMode(mode);
}

export async function deleteArticle(id) {
    if (!confirm('Ви впевнені, що хочете видалити цю статтю?')) return;
    try {
        await ArticlesApi.deleteArticle(id);
        if (typeof window !== 'undefined' && window.AdminAuth && window.AdminAuth.showToast) {
            window.AdminAuth.showToast('Статтю видалено.', 'success');
        }
        await loadArticlesData();
    } catch (err) {
        // Handled by ApiClient toast
    }
}

export async function loadCategoriesData() {
    try {
        const categories = await ArticlesApi.getCategories();
        ArticlesUI.renderCategoriesSelect(categories);
    } catch (err) {
        // Handled by ApiClient
    }
}

export async function loadArticlesData() {
    try {
        const articles = await ArticlesApi.getArticles();
        ArticlesUI.renderArticlesTable(articles);
    } catch (err) {
        // Handled by ApiClient
    }
}

if (typeof window !== 'undefined') {
    window.ArticlesApi = ArticlesApi;
    window.ArticlesUI = ArticlesUI;
    window.setUploadMode = setUploadMode;
    window.deleteArticle = deleteArticle;

    function initArticles() {
        loadCategoriesData();
        loadArticlesData();

        const addArticleForm = document.getElementById('add-article-form');
        if (addArticleForm) {
            addArticleForm.addEventListener('submit', async (e) => {
                e.preventDefault();
                const btn = document.getElementById('submit-btn');
                const title = document.getElementById('title').value;
                const categoryId = document.getElementById('category').value;
                const minAge = document.getElementById('minAge').value;
                const maxAge = document.getElementById('maxAge').value;

                try {
                    if (btn) {
                        btn.disabled = true;
                        btn.textContent = 'Обробка...';
                    }

                    if (ArticlesUI.uploadMode === 'manual') {
                        const payload = {
                            title: title,
                            categoryId: categoryId,
                            content: document.getElementById('content').value
                        };
                        await ArticlesApi.createArticle(payload);
                    } else {
                        const formData = new FormData();
                        formData.append('title', title);
                        formData.append('categoryId', categoryId);
                        formData.append('file', document.getElementById('file').files[0]);
                        if (minAge) formData.append('minAge', minAge);
                        if (maxAge) formData.append('maxAge', maxAge);

                        await ArticlesApi.uploadArticleFile(formData);
                    }

                    if (window.AdminAuth && window.AdminAuth.showToast) {
                        window.AdminAuth.showToast('Статтю збережено та векторизовано успішно!', 'success');
                    }
                    e.target.reset();
                    ArticlesUI.setUploadMode('manual');
                    await loadArticlesData();

                } catch (err) {
                    // Handled
                } finally {
                    if (btn) {
                        btn.disabled = false;
                        btn.textContent = 'Зберегти та згенерувати вектори';
                    }
                }
            });
        }

        const addCategoryForm = document.getElementById('add-category-form');
        if (addCategoryForm) {
            addCategoryForm.addEventListener('submit', async (e) => {
                e.preventDefault();
                const payload = {
                    name: document.getElementById('cat-name').value.toUpperCase(),
                    displayName: document.getElementById('cat-display').value
                };

                try {
                    await ArticlesApi.createCategory(payload);
                    if (window.AdminAuth && window.AdminAuth.showToast) {
                        window.AdminAuth.showToast('Категорію додано!', 'success');
                    }
                    e.target.reset();
                    await loadCategoriesData();
                } catch (err) {
                    // Handled
                }
            });
        }
    }

    if (!window.__VITEST__) {
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', initArticles);
        } else {
            initArticles();
        }
    }
}
