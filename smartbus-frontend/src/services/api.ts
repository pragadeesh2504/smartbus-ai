import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';

let isRedirecting = false;

/**
 * Configure request and response interceptors for Axios.
 * Handles:
 * - SEC-09: 401 Unauthorized handling (token clearing, login redirect with loop protection and debounce)
 * - SEC-09: 403 Forbidden handling (alert/event dispatch without clearing session or logging out)
 * - Automatic Authorization header injection from localStorage
 */
export function setupAxiosInterceptors() {
  // Request Interceptor: Ensure Bearer token is attached if available
  axios.interceptors.request.use(
    (config: InternalAxiosRequestConfig) => {
      const token = localStorage.getItem('accessToken');
      if (token && !config.headers.Authorization) {
        config.headers.Authorization = `Bearer ${token}`;
      }
      return config;
    },
    (error) => Promise.reject(error)
  );

  // Response Interceptor: Handle 401 and 403 globally
  axios.interceptors.response.use(
    (response) => response,
    (error: AxiosError) => {
      const status = error.response?.status;
      const url = error.config?.url || '';

      if (status === 401) {
        const isAuthEndpoint = url.includes('/api/auth/login') || url.includes('/api/auth/register');
        const isLoginPage = typeof window !== 'undefined' && window.location.pathname === '/login';

        // Do not redirect if the failure happened during login/register or user is already on /login
        if (isAuthEndpoint || isLoginPage) {
          return Promise.reject(error);
        }

        // Clean up invalid session
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        localStorage.removeItem('user');
        delete axios.defaults.headers.common['Authorization'];

        // Prevent multiple simultaneous redirects (reload/redirect storm protection)
        if (!isRedirecting) {
          isRedirecting = true;
          window.dispatchEvent(new CustomEvent('smartbus:unauthorized'));
          window.location.href = '/login';
          setTimeout(() => {
            isRedirecting = false;
          }, 3000);
        }

        return Promise.reject(error);
      }

      if (status === 403) {
        // SEC-09: Do NOT log out or redirect on 403 Forbidden
        const message = (error.response?.data as any)?.message || 'Access Denied: You do not have permission to perform this action.';
        console.warn('Access Forbidden (403):', message);

        window.dispatchEvent(
          new CustomEvent('smartbus:forbidden', {
            detail: { message, status: 403, url }
          })
        );

        return Promise.reject(error);
      }

      return Promise.reject(error);
    }
  );
}

// Configure base URL from environment variable if provided (e.g. In multi-domain deployments)
if (import.meta.env.VITE_API_BASE_URL) {
  axios.defaults.baseURL = import.meta.env.VITE_API_BASE_URL.replace(/\/+$/, '');
}

// Initialize interceptors immediately on module import
setupAxiosInterceptors();

// Export a configured axios instance for components that prefer importing api
export const api = axios;
export default api;
