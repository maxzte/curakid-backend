/**
 * Legal Documents Domain & UI Controller
 */
import { ApiClient } from './api.js';

// --- API Service Layer ---
export const PoliciesApi = {
    getDocuments: () => ApiClient.get('/admin/legal'),
    createDocument: (payload) => ApiClient.post('/admin/legal', payload),
    activateDocument: (id) => ApiClient.post(`/admin/legal/${id}/activate`),
    deactivateDocument: (id) => ApiClient.post(`/admin/legal/${id}/deactivate`)
};

// --- UI / Render Layer ---
export const PoliciesUI = {
    typeLabel(type) {
        if (type === 'TERMS') return 'Умови використання';
        if (type === 'PRIVACY_POLICY') return 'Політика конфіденційності';
        return type;
    },

    renderTable(docs) {
        const tbody = document.getElementById('policies-table-body');
        if (!tbody) return;

        const escapeHtml = (str) => (typeof window !== 'undefined' && window.AdminAuth && window.AdminAuth.escapeHtml)
            ? window.AdminAuth.escapeHtml(str) : String(str ?? '');

        if (!docs || docs.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" style="text-align: center;">Документів не знайдено.</td></tr>';
            return;
        }

        docs.sort((a, b) => {
            if (a.isActive !== b.isActive) return a.isActive ? -1 : 1;
            return new Date(b.publishedAt) - new Date(a.publishedAt);
        });

        tbody.innerHTML = docs.map(d => {
            const date = new Date(d.publishedAt).toLocaleDateString('uk-UA', {
                day: '2-digit', month: '2-digit', year: 'numeric',
                hour: '2-digit', minute: '2-digit'
            });

            const statusBadge = d.isActive
                ? '<span class="badge" style="background: var(--success, #22c55e); color: #fff;">Активний</span>'
                : '<span class="badge" style="background: var(--text-muted); color: #fff;">Неактивний</span>';

            const activateBtn = !d.isActive
                ? `<button class="edit-btn" onclick="activateDoc('${d.id}')">Активувати</button>`
                : '';

            const deactivateBtn = d.isActive
                ? `<button class="delete-btn" onclick="deactivateDoc('${d.id}')">Деактивувати</button>`
                : '';

            const safeVersion = escapeHtml(d.version);
            const safeUrl = escapeHtml(d.url);
            const shortUrl = escapeHtml(d.url.length > 40 ? d.url.substring(0, 40) + '…' : d.url);

            return `
                <tr>
                    <td style="font-weight: 600;">${this.typeLabel(d.type)}</td>
                    <td><span class="badge">${safeVersion}</span></td>
                    <td>
                        <a href="${safeUrl}" target="_blank" title="${safeUrl}"
                           style="color: var(--primary); text-decoration: none; font-size: 0.85rem;">
                            ${shortUrl}
                        </a>
                    </td>
                    <td>${statusBadge}</td>
                    <td style="font-size: 0.85rem; color: var(--text-muted);">${date}</td>
                    <td style="display: flex; gap: 0.5rem; flex-wrap: wrap;">
                        ${activateBtn}
                        ${deactivateBtn}
                    </td>
                </tr>
            `;
        }).join('');
    }
};

export async function activateDoc(id) {
    if (!confirm('Активувати цей документ? Попередня активна версія цього типу буде автоматично деактивована.')) return;
    try {
        await PoliciesApi.activateDocument(id);
        if (typeof window !== 'undefined' && window.AdminAuth && window.AdminAuth.showToast) {
            window.AdminAuth.showToast('Документ активовано', 'success');
        }
        await loadDocumentsData();
    } catch (err) {
        // Handled
    }
}

export async function deactivateDoc(id) {
    if (!confirm('Деактивувати цей документ?')) return;
    try {
        await PoliciesApi.deactivateDocument(id);
        if (typeof window !== 'undefined' && window.AdminAuth && window.AdminAuth.showToast) {
            window.AdminAuth.showToast('Документ деактивовано', 'success');
        }
        await loadDocumentsData();
    } catch (err) {
        // Handled
    }
}

export async function loadDocumentsData() {
    try {
        const docs = await PoliciesApi.getDocuments();
        PoliciesUI.renderTable(docs);
    } catch (err) {
        // Handled
    }
}

if (typeof window !== 'undefined') {
    window.PoliciesApi = PoliciesApi;
    window.PoliciesUI = PoliciesUI;
    window.activateDoc = activateDoc;
    window.deactivateDoc = deactivateDoc;

    function initPolicies() {
        loadDocumentsData();

        const policyForm = document.getElementById('policy-form');
        if (policyForm) {
            policyForm.addEventListener('submit', async (e) => {
                e.preventDefault();

                const payload = {
                    type: document.getElementById('doc-type').value,
                    version: document.getElementById('doc-version').value.trim(),
                    url: document.getElementById('doc-url').value.trim(),
                };

                if (!payload.version) {
                    if (window.AdminAuth && window.AdminAuth.showToast) {
                        window.AdminAuth.showToast('Версія обов\'язкова', 'error');
                    }
                    return;
                }

                try {
                    await PoliciesApi.createDocument(payload);
                    if (window.AdminAuth && window.AdminAuth.showToast) {
                        window.AdminAuth.showToast('Документ додано. Активуйте його якщо потрібно.', 'success');
                    }
                    policyForm.reset();
                    await loadDocumentsData();
                } catch (err) {
                    // Handled
                }
            });
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initPolicies);
    } else {
        initPolicies();
    }
}
