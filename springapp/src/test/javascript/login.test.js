/**
 * @vitest-environment jsdom
 */
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ApiClient } from '../../main/resources/static/admin/js/api.js';

describe('Admin Login Controller Unit Tests', () => {
    beforeEach(async () => {
        document.body.innerHTML = `
            <form id="login-form">
                <input id="email" value="admin@curakid.com" />
                <input id="password" value="secret123" />
                <button type="submit" id="submit-btn">Увійти</button>
            </form>
        `;

        localStorage.clear();

        window.AdminAuth = {
            showToast: vi.fn()
        };

        vi.restoreAllMocks();

        await import('../../main/resources/static/admin/js/login.js');
        document.dispatchEvent(new Event('DOMContentLoaded'));
    });

    it('should authenticate user, store tokens, and redirect on successful login', async () => {
        // Given
        const mockPost = vi.spyOn(ApiClient, 'post').mockResolvedValue({ accessToken: 'mock-jwt-token' });
        const mockGet = vi.spyOn(ApiClient, 'get').mockResolvedValue([]);

        const form = document.getElementById('login-form');

        // When
        const event = new Event('submit', { cancelable: true, bubbles: true });
        form.dispatchEvent(event);

        // Allow promises in submit listener to settle
        await new Promise(resolve => setTimeout(resolve, 50));

        // Then
        expect(mockPost).toHaveBeenCalledWith('/auth/login', {
            email: 'admin@curakid.com',
            password: 'secret123'
        });
        expect(localStorage.getItem('accessToken')).toBe('mock-jwt-token');
        expect(localStorage.getItem('curakid_admin_token')).toBe('mock-jwt-token');
        expect(mockGet).toHaveBeenCalledWith('/admin/users');
    });

    it('should display error toast and restore submit button on authentication failure', async () => {
        // Given
        vi.spyOn(console, 'error').mockImplementation(() => {});
        vi.spyOn(ApiClient, 'post').mockRejectedValue(new Error('Невірний пароль'));

        const form = document.getElementById('login-form');
        const btn = document.getElementById('submit-btn');

        // When
        const event = new Event('submit', { cancelable: true, bubbles: true });
        form.dispatchEvent(event);

        await new Promise(resolve => setTimeout(resolve, 50));

        // Then
        expect(window.AdminAuth.showToast).toHaveBeenCalledWith('Невірний пароль', 'error');
        expect(btn.disabled).toBe(false);
        expect(btn.textContent).toBe('Увійти');
    });
});
