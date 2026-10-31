/**
 * Curakid Admin Centralized API Utility
 */
const API_BASE = '/api/v1';

async function apiFetch(endpoint, options = {}) {
    let url = endpoint;
    if (url.startsWith('/api/') && !url.startsWith('/api/v1/')) {
        url = '/api/v1' + url.substring(4);
    } else if (!url.startsWith('http') && !url.startsWith('/api/v1/')) {
        url = API_BASE + (url.startsWith('/') ? url : '/' + url);
    }

    const token = localStorage.getItem('accessToken') || sessionStorage.getItem('accessToken');

    const headers = {
        'Content-Type': 'application/json',
        ...(options.headers || {})
    };

    if (token) {
        headers['Authorization'] = `Bearer ${token}`;
    }

    const response = await fetch(url, {
        ...options,
        headers
    });

    if (response.status === 401 || response.status === 403) {
        if (!window.location.pathname.includes('/admin/login')) {
            localStorage.removeItem('accessToken');
            sessionStorage.removeItem('accessToken');
            window.location.href = '/admin/login';
        }
    }

    return response;
}
