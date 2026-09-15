// Configuração central da API
const API_BASE_URL = 'http://localhost:8080/api/v1';

// Função utilitária para fazer requisições
async function apiFetch(endpoint, options = {}) {
    const token = localStorage.getItem('webchat_token');
    
    const headers = {
        'Content-Type': 'application/json',
        ...options.headers
    };

    if (token) {
        headers['Authorization'] = `Bearer ${token}`;
    }

    const config = {
        ...options,
        headers
    };

    try {
        const response = await fetch(`${API_BASE_URL}${endpoint}`, config);
        
        // Tratamento global para token expirado ou não autorizado
        if (response.status === 401) {
            localStorage.removeItem('webchat_token');
            if (!window.location.href.includes('login.html')) {
                window.location.href = '/login.html';
            }
            throw new Error('Credenciais inválidas ou sessão expirada.');
        }

        if (!response.ok) {
            let errorData;
            try {
                errorData = await response.json();
            } catch (e) {
                errorData = { message: 'Erro desconhecido na requisição.' };
            }
            throw errorData;
        }

        if (response.status === 204) return {};
        
        return await response.json();
    } catch (error) {
        console.error(`Erro na requisição ${endpoint}:`, error);
        throw error;
    }
}

// ==========================================
// SERVIÇOS (Integrados com o Backend real)
// ==========================================

const AuthService = {
    async login(email, password) {
        const data = await apiFetch('/auth/login', {
            method: 'POST',
            body: JSON.stringify({ email, password })
        });
        localStorage.setItem('webchat_token', data.token);
        return data;
    },

    async register(name, email, password) {
        return apiFetch('/auth/register', {
            method: 'POST',
            body: JSON.stringify({ name, email, password })
        });
    },
    
    logout() {
        localStorage.removeItem('webchat_token');
        window.location.href = '/login.html';
    }
};

const UserService = {
    async getMe() {
        return apiFetch('/users/me');
    },
    
    async listUsers() {
        return apiFetch('/users');
    }
};
