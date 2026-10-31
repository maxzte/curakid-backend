/**
 * Users Domain & UI Controller
 */

// --- API Service Layer ---
const UsersApi = {
    getUsers: (page = 0, email = '', size = 20) =>
        ApiClient.get(`/admin/users?page=${page}&size=${size}&email=${encodeURIComponent(email)}`),
    deleteUser: (id) => ApiClient.delete(`/admin/users/${id}`)
};

// --- UI / Render Layer ---
const UsersUI = {
    renderUsersTable(users) {
        const tbody = document.getElementById('users-table-body');
        if (!tbody) return;

        if (!users || users.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" style="text-align: center; color: var(--text-muted);">Користувачів не знайдено.</td></tr>';
            return;
        }

        tbody.innerHTML = users.map(user => {
            const isActive = user.isActive === true || user.active === true;
            const isAdmin = user.role === 'ROLE_ADMIN';
            const roleClass = isAdmin ? 'admin' : 'user';
            const roleName = isAdmin ? 'Адмін' : 'Користувач';
            const statusClass = isActive ? 'active' : 'inactive';
            const statusText = isActive ? 'Активний' : 'Неактивний';

            return `
            <tr>
                <td style="font-weight: 500;">${AdminAuth.escapeHtml(user.fullName)}</td>
                <td>${AdminAuth.escapeHtml(user.email)}</td>
                <td><span class="badge ${roleClass}">${roleName}</span></td>
                <td><span class="status-dot ${statusClass}"></span> ${statusText}</td>
                <td style="color: var(--text-muted); font-size: 0.85rem; font-family: monospace;">${AdminAuth.escapeHtml(user.id)}</td>
                <td>
                    ${isActive ? `<button class="action-btn delete-btn" onclick="deleteUser('${AdminAuth.escapeHtml(user.id)}')">Видалити</button>` : ''}
                </td>
            </tr>
        `}).join('');
    },

    renderPagination(totalPages, current, currentSearch) {
        const container = document.getElementById('pagination-container');
        if (!container) return;
        if (totalPages <= 1) {
            container.innerHTML = '';
            return;
        }

        const DOTS = '...';
        const neighbors = 2;

        const visible = new Set();
        visible.add(0);
        visible.add(totalPages - 1);
        for (let i = Math.max(0, current - neighbors); i <= Math.min(totalPages - 1, current + neighbors); i++) {
            visible.add(i);
        }

        const sorted = Array.from(visible).sort((a, b) => a - b);

        let html = '';
        html += `<button class="btn btn-sm pagination-btn ${current === 0 ? 'disabled' : ''}" onclick="fetchUsers(${current - 1}, '${currentSearch}')" ${current === 0 ? 'disabled' : ''}>←</button>`;

        let prev = null;
        sorted.forEach(i => {
            if (prev !== null && i > prev + 1) {
                html += `<span class="pagination-dots">${DOTS}</span>`;
            }
            if (i === current) {
                html += `<button class="btn btn-sm pagination-btn active" disabled>${i + 1}</button>`;
            } else {
                html += `<button class="btn btn-sm pagination-btn" onclick="fetchUsers(${i}, '${currentSearch}')">${i + 1}</button>`;
            }
            prev = i;
        });

        html += `<button class="btn btn-sm pagination-btn ${current === totalPages - 1 ? 'disabled' : ''}" onclick="fetchUsers(${current + 1}, '${currentSearch}')" ${current === totalPages - 1 ? 'disabled' : ''}>→</button>`;

        container.innerHTML = html;
    }
};

let currentPage = 0;
let currentSearch = '';

window.fetchUsers = async function(page = 0, email = '') {
    currentPage = page;
    currentSearch = email;

    try {
        const data = await UsersApi.getUsers(page, email);
        const users = data.items || [];

        UsersUI.renderUsersTable(users);
        UsersUI.renderPagination(data.totalPages || 0, data.currentPage || 0, currentSearch);
    } catch (err) {
        // Handled
    }
};

window.applySearch = function() {
    const emailInput = document.getElementById('search-email');
    const email = emailInput ? emailInput.value : '';
    window.fetchUsers(0, email);
};

window.resetSearch = function() {
    const emailInput = document.getElementById('search-email');
    if (emailInput) emailInput.value = '';
    window.fetchUsers(0, '');
};

window.deleteUser = async function(id) {
    if (!confirm('Ви впевнені, що хочете видалити цього користувача? Це призведе до анонімізації всіх його даних та даних його дітей.')) return;
    try {
        await UsersApi.deleteUser(id);
        AdminAuth.showToast('Користувача видалено та анонімізовано', 'success');
        await window.fetchUsers(currentPage, currentSearch);
    } catch (err) {
        // Handled
    }
};

document.addEventListener('DOMContentLoaded', async () => {
    await window.fetchUsers();
});
