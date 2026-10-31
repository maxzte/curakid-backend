/**
 * Admin Login Controller
 */
import { ApiClient } from './api.js';

document.addEventListener('DOMContentLoaded', () => {
    const loginForm = document.getElementById('login-form');
    if (!loginForm) return;

    loginForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const btn = document.getElementById('submit-btn');

        const payload = {
            email: document.getElementById('email').value,
            password: document.getElementById('password').value
        };

        try {
            if (btn) {
                btn.disabled = true;
                btn.textContent = 'Аутентифікація...';
            }

            // 1. Attempt login via ApiClient
            const data = await ApiClient.post('/auth/login', payload);
            const token = data.accessToken;

            // 2. Save token
            localStorage.setItem('accessToken', token);
            localStorage.setItem('curakid_admin_token', token);
            document.cookie = `admin_token=${token}; path=/; SameSite=Strict`;

            // 3. Verify admin access
            await ApiClient.get('/admin/users');

            // 4. Success -> redirect
            window.location.href = '/admin/ui/articles';

        } catch (err) {
            if (typeof window.AdminAuth !== 'undefined' && window.AdminAuth.showToast) {
                window.AdminAuth.showToast(err.message || 'Помилка входу', 'error');
            }
            if (btn) {
                btn.disabled = false;
                btn.textContent = 'Увійти';
            }
        }
    });
});
