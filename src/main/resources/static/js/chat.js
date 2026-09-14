let currentUser = null;
let activeChatUser = null;

// Inicialização
document.addEventListener('DOMContentLoaded', async () => {
    const token = localStorage.getItem('webchat_token');
    if (!token) {
        window.location.href = '/login.html';
        return;
    }

    try {
        // 1. Carregar meus dados da API Real
        currentUser = await UserService.getMe();
        document.getElementById('current-user-name').textContent = currentUser.name;
        document.getElementById('current-user-avatar').src = `https://ui-avatars.com/api/?name=${encodeURIComponent(currentUser.name)}&background=random`;
        
        // 2. Carregar contatos (outros usuários do sistema)
        await loadContacts();
    } catch (error) {
        console.error('Falha ao inicializar o chat', error);
    }
});

// Busca usuários da API e preenche a barra lateral
async function loadContacts() {
    try {
        const users = await UserService.listUsers();
        const chatList = document.getElementById('chat-list');
        chatList.innerHTML = ''; // Limpar listagem mockup
        
        if (users.length === 1) { // Só tem o próprio usuário no banco
            chatList.innerHTML = `<div style="padding: 20px; color: var(--text-muted); text-align: center; font-size: 14px;">Nenhum outro usuário cadastrado ainda.</div>`;
            return;
        }

        users.forEach(user => {
            // Esconde eu mesmo da lista
            if (user.id === currentUser.id) return;
            
            const item = document.createElement('div');
            item.className = 'chat-item';
            
            // Status visual
            const statusText = user.status === 'ONLINE' ? '🟢 Online' : '⚪ Offline';
            
            item.innerHTML = `
                <img src="https://ui-avatars.com/api/?name=${encodeURIComponent(user.name)}&background=random&color=fff" class="avatar">
                <div class="chat-item-info">
                    <div class="chat-item-name">${user.name}</div>
                    <div class="chat-item-preview">${statusText}</div>
                </div>
            `;
            
            // Lógica de clicar no chat
            item.addEventListener('click', () => {
                document.querySelectorAll('.chat-item').forEach(el => el.classList.remove('active'));
                item.classList.add('active');
                
                activeChatUser = user;
                document.getElementById('active-chat-name').textContent = user.name;
                document.getElementById('active-chat-avatar').src = `https://ui-avatars.com/api/?name=${encodeURIComponent(user.name)}&background=random&color=fff`;
                
                // Mostrar a barra superior e a barra de input
                document.getElementById('chat-header').style.display = 'flex';
                document.getElementById('chat-input-area').style.display = 'block';

                // Mostrar chat vazio (já que a fase de chat no backend ainda não existe)
                const messagesContainer = document.getElementById('chat-messages');
                messagesContainer.innerHTML = `
                    <div style="text-align: center; color: var(--text-muted); margin-top: 20px; font-size: 14px; background: rgba(0,0,0,0.2); padding: 10px; border-radius: 8px;">
                        Conversa com <strong>${user.name}</strong> iniciada.<br>
                        (O módulo de mensagens ainda não foi implementado no backend)
                    </div>
                `;
            });
            
            chatList.appendChild(item);
        });
    } catch (error) {
        console.error('Erro ao listar usuários', error);
    }
}

function logout() {
    AuthService.logout();
}

// Manipulador de envio de mensagens no front
document.getElementById('message-form')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const input = document.getElementById('message-input');
    const text = input.value.trim();
    
    if (!text) return;
    if (!activeChatUser) {
        alert('Selecione um contato para enviar mensagem.');
        return;
    }
    
    appendMessage(text, 'sent');
    
    input.value = '';
    input.focus();
});

// --- Lógica do Botão de Emoji ---
const emojiBtn = document.querySelector('button[title="Emoji"]');
const emojiContainer = document.getElementById('emoji-picker-container');
const msgInput = document.getElementById('message-input');

emojiBtn?.addEventListener('click', (e) => {
    e.preventDefault();
    e.stopPropagation(); // Impede que o clique suba para o document e feche na mesma hora
    emojiContainer.style.display = emojiContainer.style.display === 'none' ? 'block' : 'none';
});

// Quando clicar em um emoji, adiciona no input
document.querySelector('emoji-picker')?.addEventListener('emoji-click', event => {
    msgInput.value += event.detail.unicode;
    msgInput.focus();
});

// Fecha o painel de emojis ao clicar fora
document.addEventListener('click', (e) => {
    if (emojiContainer && emojiContainer.style.display === 'block') {
        if (!emojiContainer.contains(e.target)) {
            emojiContainer.style.display = 'none';
        }
    }
});

function appendMessage(text, type) {
    const messagesContainer = document.getElementById('chat-messages');
    
    // Remove o aviso inicial se existir
    const alertMsg = messagesContainer.querySelector('div[style*="text-align: center"]');
    if (alertMsg) alertMsg.remove();
    
    const msgElement = document.createElement('div');
    msgElement.className = `message ${type}`;
    
    const now = new Date();
    const time = `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}`;
    
    const readStatus = type === 'sent' 
        ? `<i class="fa-solid fa-check" style="color: rgba(255,255,255,0.6);"></i>` 
        : '';
        
    msgElement.innerHTML = `
        <p>${text.replace(/\n/g, '<br>')}</p>
        <span class="message-time">${time} ${readStatus}</span>
    `;
    
    messagesContainer.appendChild(msgElement);
    messagesContainer.scrollTop = messagesContainer.scrollHeight;
}
