/**
 * @vitest-environment jsdom
 */
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ApiClient } from '../../main/resources/static/admin/js/api.js';

describe('ApiClient Unit Tests', () => {
    beforeEach(() => {
        localStorage.clear();
        vi.restoreAllMocks();
    });

    describe('getHeaders', () => {
        it('should return application/json header when no token present', () => {
            // Given
            localStorage.clear();

            // When
            const headers = ApiClient.getHeaders();

            // Then
            expect(headers['Content-Type']).toBe('application/json');
            expect(headers['Authorization']).toBeUndefined();
        });

        it('should append Bearer token if accessToken is in localStorage', () => {
            // Given
            localStorage.setItem('accessToken', 'mock-jwt-token-123');

            // When
            const headers = ApiClient.getHeaders();

            // Then
            expect(headers['Authorization']).toBe('Bearer mock-jwt-token-123');
        });

        it('should preserve existing Bearer prefix if present in storage', () => {
            // Given
            localStorage.setItem('curakid_admin_token', 'Bearer already-prefixed-token');

            // When
            const headers = ApiClient.getHeaders();

            // Then
            expect(headers['Authorization']).toBe('Bearer already-prefixed-token');
        });
    });

    describe('request & HTTP Methods', () => {
        it('should prepend /api/v1 to endpoints and return json response', async () => {
            // Given
            const mockResponse = { items: [{ id: '1', name: 'Test' }] };
            const fetchSpy = vi.fn().mockResolvedValue({
                ok: true,
                status: 200,
                json: async () => mockResponse
            });
            vi.stubGlobal('fetch', fetchSpy);

            // When
            const result = await ApiClient.get('/admin/users');

            // Then
            expect(fetchSpy).toHaveBeenCalledWith(expect.stringContaining('/api/v1/admin/users'), expect.objectContaining({
                method: 'GET'
            }));
            expect(result).toEqual(mockResponse);
        });

        it('should handle FormData body without overriding Content-Type header', async () => {
            // Given
            const formData = new FormData();
            formData.append('title', 'Article Title');
            const fetchSpy = vi.fn().mockResolvedValue({
                ok: true,
                status: 200,
                json: async () => ({ success: true })
            });
            vi.stubGlobal('fetch', fetchSpy);

            // When
            await ApiClient.post('/admin/knowledge/upload', formData);

            // Then
            const callArgs = fetchSpy.mock.calls[0][1];
            expect(callArgs.body).toBe(formData);
            expect(callArgs.headers['Content-Type']).toBeUndefined();
        });

        it('should throw error and clear token when response status is 401 Unauthorized', async () => {
            // Given
            vi.spyOn(console, 'error').mockImplementation(() => {});
            localStorage.setItem('accessToken', 'expired-token');
            vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
                ok: false,
                status: 401,
                json: async () => ({ message: 'Unauthorized' })
            }));

            // When & Then
            await expect(ApiClient.get('/admin/users')).rejects.toThrow('Неавторизований доступ');
            expect(localStorage.getItem('accessToken')).toBeNull();
        });

        it('should throw server error message when response is not ok', async () => {
            // Given
            vi.spyOn(console, 'error').mockImplementation(() => {});
            vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
                ok: false,
                status: 500,
                json: async () => ({ message: 'Внутрішня помилка сервера' })
            }));

            // When & Then
            await expect(ApiClient.get('/admin/rules')).rejects.toThrow('Внутрішня помилка сервера');
        });

        it('should return null on HTTP 204 No Content', async () => {
            // Given
            vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
                ok: true,
                status: 204
            }));

            // When
            const result = await ApiClient.delete('/admin/users/123');

            // Then
            expect(result).toBeNull();
        });
    });
});
