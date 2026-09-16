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
function addContactToSidebar(user) {
    const chatList = document.getElementById('chat-list');
    
    // Remove "nenhuma conversa" se existir
    if (chatList.innerHTML.includes('Nenhuma conversa')) {
        chatList.innerHTML = '';
    }
    
    // Evita duplicata
    if (chatList.querySelector(`[data-user-id="${user.id}"]`)) return;

    const item = document.createElement('div');
    item.className = 'chat-item';
    
    const statusText = user.status === 'ONLINE' ? '🟢 Online' : '⚪ Offline';
    
    item.innerHTML = `
        <img src="https://ui-avatars.com/api/?name=${encodeURIComponent(user.name)}&background=random&color=fff" class="avatar">
        <div class="chat-item-info">
            <div class="chat-item-name">${user.name}</div>
            <div class="chat-item-preview">${statusText}</div>
        </div>
    `;
    
    item.dataset.userId = user.id;
    
    item.addEventListener('click', () => {
        document.querySelectorAll('.chat-item').forEach(el => el.classList.remove('active'));
        item.classList.add('active');
        openChat(user);
    });
    
    chatList.appendChild(item);
}

async function loadContacts() {
    try {
        const chatList = document.getElementById('chat-list');
        chatList.innerHTML = '';
        
        // TODO: BACKEND - Fazer requisição GET /api/v1/chats aqui para listar as conversas do usuário.
        // O payload esperado seria um array de chats, e para cada um você chamaria addContactToSidebar(chat.participant).
        // Por enquanto, usamos o localStorage para simular:
        const savedChats = JSON.parse(localStorage.getItem('mockChats')) || [];
        
        if (savedChats.length === 0) {
            chatList.innerHTML = '<div style="text-align: center; color: var(--text-muted); padding: 20px; font-size: 13px;">Nenhuma conversa.<br>Clique em "Adicionar Amigos" para começar.</div>';
            return;
        }

        savedChats.forEach(user => {
            addContactToSidebar(user);
        });
    } catch (error) {
        console.error('Erro ao carregar chats locais', error);
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
    
    // TODO: BACKEND - Fazer requisição POST /api/v1/chats/{chatId}/messages com o {content}
    // TODO: BACKEND - Implementar o envio real pelo protocolo STOMP (WebSocket)

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

// --- Lógica do Modal "Adicionar Amigos" ---
const addFriendBtn = document.getElementById('btn-add-friend');
const addFriendModal = document.getElementById('add-friend-modal');
const closeModalBtn = document.getElementById('close-modal-btn');

// Elementos das Abas
const tabSearch = document.getElementById('tab-search-friends');
const tabRequests = document.getElementById('tab-friend-requests');
const bodySearch = document.getElementById('modal-body-search');
const bodyRequests = document.getElementById('modal-body-requests');

// Busca
const friendSearchInput = document.getElementById('friend-search-input');
const friendSearchResults = document.getElementById('friend-search-results');
const friendRequestsList = document.getElementById('friend-requests-list');

let allSystemUsers = [];

// Alternar abas
tabSearch?.addEventListener('click', () => {
    tabSearch.style.borderBottomColor = 'var(--brand-blurple)';
    tabSearch.style.color = 'var(--text-main)';
    tabRequests.style.borderBottomColor = 'transparent';
    tabRequests.style.color = 'var(--text-muted)';
    bodySearch.style.display = 'block';
    bodyRequests.style.display = 'none';
});

tabRequests?.addEventListener('click', () => {
    tabRequests.style.borderBottomColor = 'var(--brand-blurple)';
    tabRequests.style.color = 'var(--text-main)';
    tabSearch.style.borderBottomColor = 'transparent';
    tabSearch.style.color = 'var(--text-muted)';
    bodySearch.style.display = 'none';
    bodyRequests.style.display = 'block';
    renderMockFriendRequests();
});

addFriendBtn?.addEventListener('click', async () => {
    addFriendModal.style.display = 'flex';
    friendSearchInput.value = '';
    tabSearch.click(); // Garante que abre na primeira aba
    
    try {
        friendSearchResults.innerHTML = '<div style="text-align: center; color: var(--text-muted); padding: 20px;">Carregando usuários...</div>';
        allSystemUsers = await UserService.listUsers();
        renderFriendResults(allSystemUsers);
    } catch (error) {
        friendSearchResults.innerHTML = '<div style="text-align: center; color: #f23f42; padding: 20px;">Erro ao carregar usuários.</div>';
    }
});

closeModalBtn?.addEventListener('click', () => {
    addFriendModal.style.display = 'none';
});

addFriendModal?.addEventListener('click', (e) => {
    if (e.target === addFriendModal) {
        addFriendModal.style.display = 'none';
    }
});

friendSearchInput?.addEventListener('input', (e) => {
    const term = e.target.value.toLowerCase();
    const filtered = allSystemUsers.filter(u => u.name.toLowerCase().includes(term) || u.email.toLowerCase().includes(term));
    renderFriendResults(filtered);
});

function renderFriendResults(users) {
    friendSearchResults.innerHTML = '';
    const others = users.filter(u => u.id !== currentUser.id);
    
    if (others.length === 0) {
        friendSearchResults.innerHTML = '<div style="text-align: center; color: var(--text-muted); padding: 20px;">Nenhum usuário encontrado.</div>';
        return;
    }
    
    others.forEach(user => {
        const item = document.createElement('div');
        item.className = 'chat-item';
        item.style.justifyContent = 'space-between';
        item.style.cursor = 'default'; // Remove o cursor de clique na linha toda
        
        item.innerHTML = `
            <div style="display: flex; align-items: center;">
                <img src="https://ui-avatars.com/api/?name=${encodeURIComponent(user.name)}&background=random&color=fff" class="avatar" style="width: 36px; height: 36px;">
                <div class="chat-item-info">
                    <div class="chat-item-name" style="margin-bottom: 0;">${user.name}</div>
                </div>
            </div>
            <button class="btn-primary btn-send-request" style="padding: 6px 12px; font-size: 12px;"><i class="fa-solid fa-user-plus"></i> Adicionar</button>
        `;
        
        // Clicar no modal
        const btnAdd = item.querySelector('.btn-send-request');
        btnAdd.addEventListener('click', () => {
            btnAdd.innerHTML = '<i class="fa-solid fa-check"></i> Enviado';
            btnAdd.style.backgroundColor = 'transparent';
            btnAdd.style.color = 'var(--text-muted)';
            btnAdd.style.border = '1px solid var(--text-muted)';
            btnAdd.disabled = true;
            
            // TODO: BACKEND - Fazer requisição POST /api/v1/friends/request com user.id
            console.log(`Convite de amizade (mock) enviado para: ${user.name}`);
        });
        
        friendSearchResults.appendChild(item);
    });
}

function renderMockFriendRequests() {
    friendRequestsList.innerHTML = '';
    
    // TODO: BACKEND - Chamar GET /api/v1/friends/requests em vez de usar esse mock fixo
    const mockUser = { id: 999, name: 'Usuário Misterioso', status: 'ONLINE' };
    
    const item = document.createElement('div');
    item.className = 'chat-item';
    item.style.justifyContent = 'space-between';
    item.style.cursor = 'default';
    
    item.innerHTML = `
        <div style="display: flex; align-items: center;">
            <img src="https://ui-avatars.com/api/?name=User+M&background=f23f42&color=fff" class="avatar" style="width: 36px; height: 36px;">
            <div class="chat-item-name" style="margin-bottom: 0;">Usuário Misterioso</div>
        </div>
        <div style="display: flex; gap: 8px;">
            <button class="btn-primary btn-accept" style="padding: 6px 12px; font-size: 12px; background-color: var(--brand-green);"><i class="fa-solid fa-check"></i></button>
            <button class="btn-secondary btn-reject" style="padding: 6px 12px; font-size: 12px; color: #f23f42; border: 1px solid #f23f42;"><i class="fa-solid fa-xmark"></i></button>
        </div>
    `;
    
    const btnAccept = item.querySelector('.btn-accept');
    const btnReject = item.querySelector('.btn-reject');
    
    btnAccept.addEventListener('click', () => {
        item.innerHTML = '<div style="padding: 10px; color: var(--brand-green); font-size: 13px; text-align: center; width: 100%;">Convite aceito! Usuário adicionado à sua lista.</div>';
        
        // TODO: BACKEND - Fazer requisição POST /api/v1/friends/accept com mockUser.id
        let savedChats = JSON.parse(localStorage.getItem('mockChats')) || [];
        if (!savedChats.find(u => u.id === mockUser.id)) {
            savedChats.push(mockUser);
            localStorage.setItem('mockChats', JSON.stringify(savedChats));
        }
        addContactToSidebar(mockUser);
        
        const notifBadge = document.querySelector('#tab-friend-requests span');
        if (notifBadge) notifBadge.style.display = 'none';
        
        const mainBadge = document.getElementById('friend-request-badge');
        if (mainBadge) mainBadge.style.display = 'none';
    });
    
    btnReject.addEventListener('click', () => {
        item.innerHTML = '<div style="padding: 10px; color: var(--text-muted); font-size: 13px; text-align: center; width: 100%;">Convite recusado.</div>';
        // TODO: BACKEND - Fazer requisição POST /api/v1/friends/reject com mockUser.id
        const notifBadge = document.querySelector('#tab-friend-requests span');
        if (notifBadge) notifBadge.style.display = 'none';
        
        const mainBadge = document.getElementById('friend-request-badge');
        if (mainBadge) mainBadge.style.display = 'none';
    });
    
    friendRequestsList.appendChild(item);
}

// Extraímos a lógica de abrir chat para poder chamar tanto do modal quanto da barra lateral
function openChat(user) {
    activeChatUser = user;
    
    // Atualiza o Header
    document.getElementById('chat-header').style.display = 'flex';
    document.getElementById('active-chat-name').textContent = user.name;
    document.getElementById('active-chat-avatar').src = `https://ui-avatars.com/api/?name=${encodeURIComponent(user.name)}&background=random&color=fff`;
    
    // Mostra o input e limpa
    document.getElementById('chat-input-area').style.display = 'block';
    document.getElementById('message-input').value = '';
    
    const messagesContainer = document.getElementById('chat-messages');
    
    // TODO: BACKEND - Quando a rota de chats existir, trocar isso por GET /api/v1/chats/{chatId}/messages
    // O mock atual apenas mostra uma mensagem de boas vindas para fingir o histórico
    messagesContainer.innerHTML = `
        <div style="text-align: center; color: var(--text-muted); font-size: 13px; margin: 20px 0;">
            Você iniciou uma conversa com ${user.name}.
        </div>
    `;
    
    // Foco no input
    document.getElementById('message-input').focus();
    
    // Opcional: tentar marcar o usuário na barra lateral como ativo se ele estiver lá
    document.querySelectorAll('#chat-list .chat-item').forEach(el => {
        el.classList.remove('active');
        if (el.dataset.userId == user.id) {
            el.classList.add('active');
        }
    });
}

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

// --- Lógica do Modal "Novo Grupo" (Interface Pronta para Backend) ---
const btnNewGroup = document.getElementById('btn-new-group');
const newGroupModal = document.getElementById('new-group-modal');
const closeGroupModalBtn = document.getElementById('close-group-modal-btn');
const groupParticipantsList = document.getElementById('group-participants-list');
const btnCreateGroup = document.getElementById('btn-create-group');
const groupNameInput = document.getElementById('group-name-input');

let selectedGroupUsers = new Set();

btnNewGroup?.addEventListener('click', async () => {
    newGroupModal.style.display = 'flex';
    groupNameInput.value = '';
    selectedGroupUsers.clear();
    
    try {
        groupParticipantsList.innerHTML = '<div style="text-align: center; color: var(--text-muted); padding: 20px;">Carregando usuários...</div>';
        if (allSystemUsers.length === 0) {
            allSystemUsers = await UserService.listUsers();
        }
        renderGroupParticipants(allSystemUsers);
    } catch (error) {
        groupParticipantsList.innerHTML = '<div style="text-align: center; color: #f23f42; padding: 20px;">Erro ao carregar usuários.</div>';
    }
});

closeGroupModalBtn?.addEventListener('click', () => {
    newGroupModal.style.display = 'none';
});

newGroupModal?.addEventListener('click', (e) => {
    if (e.target === newGroupModal) {
        newGroupModal.style.display = 'none';
    }
});

function renderGroupParticipants(users) {
    groupParticipantsList.innerHTML = '';
    
    const others = users.filter(u => u.id !== currentUser.id);
    
    others.forEach(user => {
        const item = document.createElement('div');
        item.className = 'chat-item';
        item.style.justifyContent = 'space-between';
        
        item.innerHTML = `
            <div style="display: flex; align-items: center;">
                <img src="https://ui-avatars.com/api/?name=${encodeURIComponent(user.name)}&background=random&color=fff" class="avatar" style="width: 36px; height: 36px;">
                <div class="chat-item-name" style="margin-bottom: 0;">${user.name}</div>
            </div>
            <input type="checkbox" style="width: 18px; height: 18px; accent-color: var(--brand-blurple); cursor: pointer;" value="${user.id}">
        `;
        
        const checkbox = item.querySelector('input[type="checkbox"]');
        
        // Clicar na linha marca/desmarca o checkbox
        item.addEventListener('click', (e) => {
            if (e.target !== checkbox) {
                checkbox.checked = !checkbox.checked;
            }
            if (checkbox.checked) {
                selectedGroupUsers.add(user.id);
            } else {
                selectedGroupUsers.delete(user.id);
            }
        });
        
        checkbox.addEventListener('change', () => {
            if (checkbox.checked) {
                selectedGroupUsers.add(user.id);
            } else {
                selectedGroupUsers.delete(user.id);
            }
        });
        
        groupParticipantsList.appendChild(item);
    });
}

// Simula a criação preparando o payload JSON
btnCreateGroup?.addEventListener('click', () => {
    const groupName = groupNameInput.value.trim();
    if (!groupName) {
        alert('Por favor, digite um nome para o grupo.');
        return;
    }
    if (selectedGroupUsers.size === 0) {
        alert('Selecione pelo menos um participante para o grupo.');
        return;
    }
    
    // Objeto pronto para ser enviado via fetch() quando a rota existir
    const payload = {
        name: groupName,
        participantIds: Array.from(selectedGroupUsers)
    };
    
    // TODO: BACKEND - Fazer requisição POST /api/v1/chats enviando este payload JSON
    console.log("Payload preparado para o backend:", payload);
    alert(`Tudo pronto!\nNome: ${groupName}\nParticipantes: ${selectedGroupUsers.size}\nAbra o console (F12) para ver o JSON montado e pronto para envio à futura rota POST /api/v1/chats.`);
    
    newGroupModal.style.display = 'none';
});

// --- Lógica do Header (Opções e Mídia) ---
const btnMediaModal = document.getElementById('btn-media-modal');
const mediaModal = document.getElementById('media-modal');
const closeMediaModalBtn = document.getElementById('close-media-modal-btn');

const mediaTabPhotos = document.getElementById('media-tab-photos');
const mediaTabLinks = document.getElementById('media-tab-links');
const mediaContentPhotos = document.getElementById('media-content-photos');
const mediaContentLinks = document.getElementById('media-content-links');

btnMediaModal?.addEventListener('click', () => {
    mediaModal.style.display = 'flex';
});
closeMediaModalBtn?.addEventListener('click', () => {
    mediaModal.style.display = 'none';
});
mediaModal?.addEventListener('click', (e) => {
    if (e.target === mediaModal) mediaModal.style.display = 'none';
});

// Alternar abas do modal de mídia
mediaTabPhotos?.addEventListener('click', () => {
    mediaTabPhotos.style.borderBottomColor = 'var(--brand-blurple)';
    mediaTabPhotos.style.color = 'var(--text-main)';
    mediaTabLinks.style.borderBottomColor = 'transparent';
    mediaTabLinks.style.color = 'var(--text-muted)';
    mediaContentPhotos.style.display = 'block';
    mediaContentLinks.style.display = 'none';
});

mediaTabLinks?.addEventListener('click', () => {
    mediaTabLinks.style.borderBottomColor = 'var(--brand-blurple)';
    mediaTabLinks.style.color = 'var(--text-main)';
    mediaTabPhotos.style.borderBottomColor = 'transparent';
    mediaTabPhotos.style.color = 'var(--text-muted)';
    mediaContentPhotos.style.display = 'none';
    mediaContentLinks.style.display = 'block';
});

const btnChatOptions = document.getElementById('btn-chat-options');
const chatOptionsDropdown = document.getElementById('chat-options-dropdown');
const btnRemoveFriend = document.getElementById('btn-remove-friend');

btnChatOptions?.addEventListener('click', (e) => {
    e.stopPropagation();
    chatOptionsDropdown.style.display = chatOptionsDropdown.style.display === 'none' ? 'block' : 'none';
});

// Fechar dropdown ao clicar fora
document.addEventListener('click', (e) => {
    if (chatOptionsDropdown && chatOptionsDropdown.style.display === 'block') {
        if (!btnChatOptions.contains(e.target) && !chatOptionsDropdown.contains(e.target)) {
            chatOptionsDropdown.style.display = 'none';
        }
    }
});

btnRemoveFriend?.addEventListener('click', () => {
    if (!activeChatUser) return;
    if(confirm(`Deseja realmente excluir o contato ${activeChatUser.name} da sua lista?`)) {
        // Remover do localStorage mock
        let savedChats = JSON.parse(localStorage.getItem('mockChats')) || [];
        savedChats = savedChats.filter(u => u.id !== activeChatUser.id);
        localStorage.setItem('mockChats', JSON.stringify(savedChats));
        
        // Esconde chat e recarrega sidebar
        document.getElementById('chat-header').style.display = 'none';
        document.getElementById('chat-input-area').style.display = 'none';
        document.getElementById('chat-messages').innerHTML = '<div style="text-align: center; color: var(--text-muted); margin-top: 50px;">Selecione um contato na barra lateral para começar a conversar.</div>';
        chatOptionsDropdown.style.display = 'none';
        activeChatUser = null;
        loadContacts();
    }
});

// --- Lógica do Perfil do Usuário ---
const profileMenuTrigger = document.getElementById('profile-menu-trigger');
const profileDropdown = document.getElementById('profile-dropdown');
const btnOpenProfile = document.getElementById('btn-open-profile');
const btnDeleteAccount = document.getElementById('btn-delete-account');
const profileModal = document.getElementById('profile-modal');
const closeProfileModalBtn = document.getElementById('close-profile-modal-btn');
const profileNameInput = document.getElementById('profile-name-input');
const btnSaveProfile = document.getElementById('btn-save-profile');
const profileErrorMsg = document.getElementById('profile-error-msg');
const profileModalAvatar = document.getElementById('profile-modal-avatar');
const avatarZoomSlider = document.getElementById('avatar-zoom-slider');

profileMenuTrigger?.addEventListener('click', (e) => {
    e.stopPropagation();
    profileDropdown.style.display = profileDropdown.style.display === 'none' ? 'block' : 'none';
});

document.addEventListener('click', (e) => {
    if (profileDropdown && profileDropdown.style.display === 'block') {
        if (!profileMenuTrigger.contains(e.target) && !profileDropdown.contains(e.target)) {
            profileDropdown.style.display = 'none';
        }
    }
});

btnOpenProfile?.addEventListener('click', () => {
    profileDropdown.style.display = 'none';
    profileModal.style.display = 'flex';
    profileNameInput.value = currentUser.name;
    profileErrorMsg.style.display = 'none';
    profileModalAvatar.src = `https://ui-avatars.com/api/?name=${encodeURIComponent(currentUser.name)}&background=random&color=fff&size=200`;
    avatarZoomSlider.value = 1;
    profileModalAvatar.style.transform = `scale(1)`;
});

closeProfileModalBtn?.addEventListener('click', () => {
    profileModal.style.display = 'none';
});

profileModal?.addEventListener('click', (e) => {
    if (e.target === profileModal) profileModal.style.display = 'none';
});

// Mock visual de zoom na foto
avatarZoomSlider?.addEventListener('input', (e) => {
    profileModalAvatar.style.transform = `scale(${e.target.value})`;
});

// TODO: BACKEND - Quando a rota de UPLOAD DE FOTO for criada, chame-a aqui.
document.getElementById('avatar-upload-mock')?.addEventListener('change', (e) => {
    if (e.target.files && e.target.files[0]) {
        // Mock visual
        const reader = new FileReader();
        reader.onload = (event) => profileModalAvatar.src = event.target.result;
        reader.readAsDataURL(e.target.files[0]);
    }
});

// Salvar Alteração de Nome REAL na API (Já integrado!)
btnSaveProfile?.addEventListener('click', async () => {
    const newName = profileNameInput.value.trim();
    if (!newName) {
        profileErrorMsg.textContent = "O nome não pode ficar vazio.";
        profileErrorMsg.style.display = 'block';
        return;
    }

    try {
        btnSaveProfile.disabled = true;
        btnSaveProfile.textContent = 'Salvando...';
        profileErrorMsg.style.display = 'none';

        // Chamada real para a API
        const updatedUser = await UserService.updateProfile({ name: newName });
        
        currentUser.name = updatedUser.name;
        localStorage.setItem('user', JSON.stringify(currentUser));
        
        document.getElementById('current-user-name').textContent = currentUser.name;
        document.getElementById('current-user-avatar').src = `https://ui-avatars.com/api/?name=${encodeURIComponent(currentUser.name)}&background=random&color=fff`;
        
        alert("Perfil atualizado com sucesso!");
        profileModal.style.display = 'none';
        
    } catch (error) {
        profileErrorMsg.textContent = error.message || "Erro ao atualizar perfil.";
        profileErrorMsg.style.display = 'block';
    } finally {
        btnSaveProfile.disabled = false;
        btnSaveProfile.textContent = 'Salvar Alterações';
    }
});

// TODO: BACKEND - Quando o MVP suportar deleção, alterar esse mock para DELETE /api/v1/users/me
btnDeleteAccount?.addEventListener('click', () => {
    profileDropdown.style.display = 'none';
    const firstConfirm = confirm("ATENÇÃO: Você tem certeza que deseja EXCLUIR sua conta?\n(Lembre-se: O backend atual não possui rota de exclusão no MVP. Isso apenas limpará seu cache e deslogará.)");
    
    if (firstConfirm) {
        const secondConfirm = prompt("Para confirmar a exclusão, digite a palavra 'EXCLUIR':");
        if (secondConfirm === 'EXCLUIR') {
            alert("Sua conta foi removida com sucesso. (Simulação Frontend)");
            logout();
        } else if (secondConfirm !== null) {
            alert("Palavra incorreta. A exclusão foi cancelada.");
        }
    }
});
