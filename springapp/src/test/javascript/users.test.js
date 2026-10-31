import { describe, it, expect, beforeEach, vi } from 'vitest';
import { UsersApi, UsersUI } from '../../main/resources/static/admin/js/users.js';
import { ApiClient } from '../../main/resources/static/admin/js/api.js';

describe('Users Module Unit Tests', () => {
    beforeEach(() => {
        vi.restoreAllMocks();

        document.body.innerHTML = `
            <table>
                <tbody id="users-table-body"></tbody>
            </table>
            <div id="pagination-container"></div>
            <input type="text" id="search-email" value="" />
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

    describe('UsersApi', () => {
        it('should call ApiClient.get with correct query params', async () => {
            // Given
            const spy = vi.spyOn(ApiClient, 'get').mockResolvedValue({ items: [], totalPages: 0 });

            // When
            await UsersApi.getUsers(1, 'test@example.com', 20);

            // Then
            expect(spy).toHaveBeenCalledWith('/admin/users?page=1&size=20&email=test%40example.com');
        });

        it('should call ApiClient.delete with user id', async () => {
            // Given
            const spy = vi.spyOn(ApiClient, 'delete').mockResolvedValue(null);

            // When
            await UsersApi.deleteUser('usr-uuid-123');

            // Then
            expect(spy).toHaveBeenCalledWith('/admin/users/usr-uuid-123');
        });
    });

    describe('UsersUI.renderUsersTable', () => {
        it('should render empty state row when users array is empty', () => {
            // Given
            const tbody = document.getElementById('users-table-body');

            // When
            UsersUI.renderUsersTable([]);

            // Then
            expect(tbody.innerHTML).toContain('Користувачів не знайдено');
        });

        it('should render table rows with user data when users array is provided', () => {
            // Given
            const tbody = document.getElementById('users-table-body');
            const mockUsers = [
                { id: '1', fullName: 'Олена Іванова', email: 'olena@example.com', role: 'ROLE_USER', isActive: true },
                { id: '2', fullName: 'Адмін Сергій', email: 'admin@example.com', role: 'ROLE_ADMIN', isActive: false }
            ];

            // When
            UsersUI.renderUsersTable(mockUsers);

            // Then
            expect(tbody.children.length).toBe(2);
            expect(tbody.innerHTML).toContain('Олена Іванова');
            expect(tbody.innerHTML).toContain('olena@example.com');
            expect(tbody.innerHTML).toContain('Адмін Сергій');
            expect(tbody.innerHTML).toContain('badge admin');
        });

        it('should sanitize XSS payload in user fields via AdminAuth.escapeHtml', () => {
            // Given
            const tbody = document.getElementById('users-table-body');
            const xssUser = [
                { id: '3', fullName: '<script>alert("xss")</script>', email: 'hacker@test.com', role: 'ROLE_USER', isActive: true }
            ];

            // When
            UsersUI.renderUsersTable(xssUser);

            // Then
            expect(tbody.querySelector('script')).toBeNull();
            expect(tbody.textContent).toContain('<script>alert("xss")</script>');
        });
    });

    describe('UsersUI.renderPagination', () => {
        it('should clear container if totalPages is 1 or less', () => {
            // Given
            const container = document.getElementById('pagination-container');
            container.innerHTML = '<button>Old</button>';

            // When
            UsersUI.renderPagination(1, 0, '');

            // Then
            expect(container.innerHTML).toBe('');
        });

        it('should render pagination buttons and disable previous on first page', () => {
            // Given
            const container = document.getElementById('pagination-container');

            // When
            UsersUI.renderPagination(5, 0, '');

            // Then
            expect(container.innerHTML).toContain('disabled');
            expect(container.innerHTML).toContain('←');
            expect(container.innerHTML).toContain('→');
        });

        it('should render pagination buttons and disable next on last page', () => {
            // Given
            const container = document.getElementById('pagination-container');

            // When
            UsersUI.renderPagination(5, 4, '');

            // Then
            const buttons = container.querySelectorAll('button');
            const lastBtn = buttons[buttons.length - 1];
            expect(lastBtn.hasAttribute('disabled')).toBe(true);
        });
    });
});
