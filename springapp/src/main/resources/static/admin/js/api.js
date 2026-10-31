/**
 * Curakid Admin Centralized API Client
 */
export const ApiClient = {
    baseUrl: '/api/v1',

    getHeaders(contentType = 'application/json') {
        const token = localStorage.getItem('accessToken') || localStorage.getItem('curakid_admin_token');
        const headers = {};
        if (contentType) {
            headers['Content-Type'] = contentType;
        }
        if (token) {
            headers['Authorization'] = token.startsWith('Bearer ') ? token : 'Bearer ' + token;
        }
        return headers;
    },

    async request(endpoint, options = {}) {
        let url = endpoint;
        if (!url.startsWith('http')) {
            let path = endpoint;
            if (path.startsWith('/api/v1/')) {
                path = path;
            } else if (path.startsWith('/api/')) {
                path = '/api/v1' + path.substring(4);
            } else {
                path = this.baseUrl + (path.startsWith('/') ? path : '/' + path);
            }
            const origin = (typeof window !== 'undefined' && window.location && window.location.origin && window.location.origin !== 'null' && window.location.origin !== 'about:')
                ? window.location.origin
                : 'http://localhost';
            url = origin + path;
        }

        const isFormData = options.body instanceof FormData;
        const contentType = isFormData ? null : 'application/json';
        const headers = {
            ...this.getHeaders(contentType),
            ...(options.headers || {})
        };

        const config = {
            ...options,
            headers
        };

        if (options.body && !isFormData && typeof options.body === 'object') {
            config.body = JSON.stringify(options.body);
        }

        try {
            const response = await fetch(url, config);

            if (response.status === 401 || response.status === 403) {
                if (typeof window !== 'undefined' && window.location && !window.location.pathname.includes('/admin/login') && !window.location.pathname.includes('/admin/ui/login')) {
                    localStorage.removeItem('accessToken');
                    localStorage.removeItem('curakid_admin_token');
                    window.location.href = '/admin/ui/login';
                }
                throw new Error("Неавторизований доступ або сесія вичерпана.");
            }

            if (!response.ok) {
                const errorData = await response.json().catch(() => ({}));
                throw new Error(errorData.message || `Помилка сервера (${response.status})`);
            }

            if (response.status === 204) return null;
            return await response.json();
        } catch (error) {
            console.error(`API Error [${endpoint}]:`, error);
            if (typeof window !== 'undefined' && typeof window.AdminAuth !== 'undefined' && window.AdminAuth.showToast) {
                window.AdminAuth.showToast(error.message || "Помилка з'єднання з сервером", 'error');
            }
            throw error;
        }
    },

    get(endpoint, options = {}) {
        return this.request(endpoint, { ...options, method: 'GET' });
    },

    post(endpoint, body, options = {}) {
        return this.request(endpoint, { ...options, method: 'POST', body });
    },

    put(endpoint, body, options = {}) {
        return this.request(endpoint, { ...options, method: 'PUT', body });
    },

    delete(endpoint, options = {}) {
        return this.request(endpoint, { ...options, method: 'DELETE' });
    }
};

if (typeof window !== 'undefined') {
    window.ApiClient = ApiClient;
}
