// Alterna entre a visualização de Login e Cadastro
function toggleAuth() {
    const loginBox = document.getElementById('login-box');
    const registerBox = document.getElementById('register-box');
    
    if (loginBox.style.display === 'none') {
        loginBox.style.display = 'block';
        registerBox.style.display = 'none';
    } else {
        loginBox.style.display = 'none';
        registerBox.style.display = 'block';
    }
}

// Manipulador do formulário de Login
document.getElementById('login-form')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const email = document.getElementById('email').value;
    const password = document.getElementById('password').value;
    const errorMsg = document.getElementById('login-error');
    const btn = e.target.querySelector('button');
    
    try {
        btn.disabled = true;
        btn.textContent = 'Carregando...';
        errorMsg.style.display = 'none';
        
        await AuthService.login(email, password);
        window.location.href = '/chat.html'; // Redireciona em caso de sucesso
    } catch (error) {
        let msg = error.message || 'Erro inesperado';
        if (error.fields) {
            const fieldErrors = Object.entries(error.fields)
                .map(([field, err]) => `${field}: ${err}`)
                .join(', ');
            msg += ` (${fieldErrors})`;
        }
        errorMsg.textContent = msg;
        errorMsg.style.display = 'block';
    } finally {
        btn.disabled = false;
        btn.textContent = 'Entrar';
    }
});

// Manipulador do formulário de Cadastro
document.getElementById('register-form')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const name = document.getElementById('reg-name').value;
    const email = document.getElementById('reg-email').value;
    const password = document.getElementById('reg-password').value;
    const errorMsg = document.getElementById('register-error');
    const btn = e.target.querySelector('button');
    
    try {
        btn.disabled = true;
        btn.textContent = 'Carregando...';
        errorMsg.style.display = 'none';
        
        await AuthService.register(name, email, password);
        
        alert('Conta criada com sucesso! Faça login para continuar.');
        toggleAuth();
        document.getElementById('email').value = email; // Preenche o email pra facilitar
    } catch (error) {
        let msg = error.message || 'Erro ao criar conta';
        if (error.fields) {
            const fieldErrors = Object.entries(error.fields)
                .map(([field, err]) => `${field}: ${err}`)
                .join(' | ');
            msg += ` (${fieldErrors})`;
        }
        errorMsg.textContent = msg;
        errorMsg.style.display = 'block';
    } finally {
        btn.disabled = false;
        btn.textContent = 'Cadastrar';
    }
});
