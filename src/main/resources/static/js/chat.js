let currentUser = null;
let contacts = [];
let chatsByContactId = new Map();
let activeContact = null;
let activeChat = null;
let nextBefore = null;
let realtimeConnectedBefore = false;

const chatList = document.getElementById('chat-list');
const messagesContainer = document.getElementById('chat-messages');
const searchInput = document.getElementById('search-input');
const msgInput = document.getElementById('message-input');

document.addEventListener('DOMContentLoaded', async () => {
    const token = localStorage.getItem('webchat_token');
    if (!token) {
        window.location.href = '/login.html';
        return;
    }

    try {
        currentUser = await UserService.getMe();
        document.getElementById('current-user-name').textContent = currentUser.name;
        document.getElementById('current-user-avatar').src = avatarUrl(currentUser.name);

        await refreshSidebar();

        Realtime.connect({
            onConnect: handleRealtimeConnected,
            onMessage: handleIncomingMessage,
            onRead: handleReadReceipt,
            onPresence: handlePresence,
            onError: handleRealtimeError
        });
    } catch (error) {
        console.error('Falha ao inicializar o chat', error);
    }
});

searchInput?.addEventListener('input', renderSidebar);

document.addEventListener('visibilitychange', () => {
    if (!document.hidden && activeChat?.unreadCount > 0) {
        markActiveChatAsRead();
    }
});

async function refreshSidebar() {
    const [users, chats] = await Promise.all([UserService.listUsers(), ChatService.listChats()]);

    contacts = users.filter(user => user.id !== currentUser.id);
    chatsByContactId = new Map(chats.map(chat => [otherParticipant(chat).id, chat]));

    if (activeChat) {
        activeChat = findChatById(activeChat.id) ?? activeChat;
    }

    renderSidebar();
}

// --- Tempo real ---
async function handleRealtimeConnected() {
    // Na reconexão, recarrega o que pode ter chegado enquanto a conexão estava fora.
    if (realtimeConnectedBefore) {
        await refreshSidebar();
        if (activeContact) openConversation(activeContact);
    }
    realtimeConnectedBefore = true;
}

async function handleIncomingMessage(message) {
    const fromMe = message.senderId === currentUser.id;
    const chat = findChatById(message.chatId);

    if (!chat) {
        // Conversa nova iniciada por outra pessoa: a listagem já traz a mensagem e as não lidas.
        await refreshSidebar();
        return;
    }

    chat.lastMessage = message;
    chat.updatedAt = message.createdAt;

    if (activeChat?.id === message.chatId) {
        appendMessage(message);
        if (!fromMe) {
            chat.unreadCount += 1;
            if (!document.hidden) markActiveChatAsRead();
        }
    } else if (!fromMe) {
        chat.unreadCount += 1;
    }

    renderSidebar();
}

function handleReadReceipt(receipt) {
    const chat = findChatById(receipt.chatId);

    if (receipt.readerId === currentUser.id) {
        // Lidas em outra aba ou dispositivo do próprio usuário.
        if (chat) chat.unreadCount = 0;
        renderSidebar();
        return;
    }

    if (chat?.lastMessage?.senderId === currentUser.id) {
        chat.lastMessage.readAt = receipt.readAt;
    }

    if (activeChat?.id === receipt.chatId) {
        messagesContainer.querySelectorAll('.message.sent i.fa-check').forEach(icon => {
            icon.className = 'fa-solid fa-check-double';
            icon.title = 'Lida';
        });
    }
}

function handlePresence(presence) {
    const contact = contacts.find(c => c.id === presence.userId);
    if (contact) contact.status = presence.status;

    chatsByContactId.forEach(chat => chat.participants
        .filter(participant => participant.id === presence.userId)
        .forEach(participant => participant.status = presence.status));

    if (activeContact?.id === presence.userId) {
        activeContact.status = presence.status;
        renderActiveContactStatus();
    }

    renderSidebar();
}

function handleRealtimeError(error) {
    let msg = error.message || 'Não foi possível enviar a mensagem.';
    if (error.fields) {
        msg += ` (${Object.values(error.fields).join(', ')})`;
    }
    alert(msg);
}

async function markActiveChatAsRead() {
    const chat = activeChat;
    chat.unreadCount = 0;
    renderSidebar();

    try {
        await ChatService.markAsRead(chat.id);
    } catch (error) {
        console.error('Falha ao marcar mensagens como lidas', error);
    }
}

function renderSidebar() {
    chatList.replaceChildren();

    if (contacts.length === 0) {
        chatList.appendChild(infoMessage('Nenhum outro usuário cadastrado ainda.'));
        return;
    }

    const filter = (searchInput?.value || '').trim().toLowerCase();

    [...contacts]
        .sort(byLastActivity)
        .filter(contact => contact.name.toLowerCase().includes(filter))
        .forEach(contact => chatList.appendChild(buildContactItem(contact)));
}

function byLastActivity(a, b) {
    const chatA = chatsByContactId.get(a.id);
    const chatB = chatsByContactId.get(b.id);
    const timeA = chatA ? Date.parse(chatA.updatedAt) : 0;
    const timeB = chatB ? Date.parse(chatB.updatedAt) : 0;
    return timeB - timeA || a.name.localeCompare(b.name);
}

function buildContactItem(contact) {
    const chat = chatsByContactId.get(contact.id);

    const item = element('div', 'chat-item');
    if (activeContact?.id === contact.id) item.classList.add('active');
    if (contact.status === 'ONLINE') item.classList.add('online');

    const avatar = element('img', 'avatar');
    avatar.src = avatarUrl(contact.name);

    const info = element('div', 'chat-item-info');
    info.appendChild(element('div', 'chat-item-name', contact.name));
    info.appendChild(element('div', 'chat-item-preview', previewText(contact, chat)));

    item.append(avatar, info);

    if (chat?.unreadCount > 0) {
        item.appendChild(element('span', 'unread-badge', String(chat.unreadCount)));
    }

    item.addEventListener('click', () => openConversation(contact));
    return item;
}

function previewText(contact, chat) {
    if (chat?.lastMessage) {
        const prefix = chat.lastMessage.senderId === currentUser.id ? 'Você: ' : '';
        return prefix + chat.lastMessage.content;
    }
    return contact.status === 'ONLINE' ? '🟢 Online' : '⚪ Offline';
}

async function openConversation(contact) {
    activeContact = contact;
    activeChat = null;
    renderSidebar();

    document.getElementById('active-chat-name').textContent = contact.name;
    document.getElementById('active-chat-avatar').src = avatarUrl(contact.name);
    renderActiveContactStatus();
    document.getElementById('chat-header').style.display = 'flex';
    document.getElementById('chat-input-area').style.display = 'block';

    messagesContainer.replaceChildren(infoMessage('Carregando mensagens...'));

    try {
        const chat = await ChatService.open(contact.id);
        const history = await ChatService.history(chat.id);
        if (activeContact !== contact) return;

        activeChat = chat;
        chatsByContactId.set(contact.id, chat);
        renderHistory(history);

        if (chat.unreadCount > 0) {
            await markActiveChatAsRead();
        }
        renderSidebar();
    } catch (error) {
        messagesContainer.replaceChildren(infoMessage('Não foi possível abrir a conversa.'));
    }
}

function renderHistory(history) {
    messagesContainer.replaceChildren();
    nextBefore = history.nextBefore;

    if (history.hasMore) {
        messagesContainer.appendChild(loadMoreButton());
    }

    if (history.messages.length === 0) {
        messagesContainer.appendChild(
            infoMessage(`Nenhuma mensagem com ${activeContact.name} ainda. Diga oi!`, 'chat-empty-state'));
    }

    history.messages.forEach(message => messagesContainer.appendChild(buildMessage(message)));
    scrollToBottom();
}

function loadMoreButton() {
    const button = element('button', 'btn-secondary load-more', 'Carregar mensagens anteriores');
    button.type = 'button';
    button.addEventListener('click', () => loadOlderMessages(button));
    return button;
}

async function loadOlderMessages(button) {
    const chatId = activeChat.id;
    button.disabled = true;

    try {
        const history = await ChatService.history(chatId, nextBefore);
        if (activeChat?.id !== chatId) return;

        const previousHeight = messagesContainer.scrollHeight;
        button.remove();
        nextBefore = history.nextBefore;

        const fragment = document.createDocumentFragment();
        if (history.hasMore) fragment.appendChild(loadMoreButton());
        history.messages.forEach(message => fragment.appendChild(buildMessage(message)));

        messagesContainer.prepend(fragment);
        messagesContainer.scrollTop = messagesContainer.scrollHeight - previousHeight;
    } catch (error) {
        button.disabled = false;
    }
}

function appendMessage(message) {
    if (messagesContainer.querySelector(`[data-message-id="${message.id}"]`)) return;

    messagesContainer.querySelector('.chat-empty-state')?.remove();
    messagesContainer.appendChild(buildMessage(message));
    scrollToBottom();
}

function renderActiveContactStatus() {
    document.getElementById('active-chat-status').textContent =
        activeContact.status === 'ONLINE' ? 'online' : 'offline';
}

function buildMessage(message) {
    const sent = message.senderId === currentUser.id;
    const bubble = element('div', `message ${sent ? 'sent' : 'received'}`);
    bubble.dataset.messageId = message.id;

    bubble.appendChild(element('p', null, message.content));

    const time = element('span', 'message-time', formatTime(message.createdAt) + ' ');
    if (sent) {
        const check = element('i', message.readAt ? 'fa-solid fa-check-double' : 'fa-solid fa-check');
        check.title = message.readAt ? 'Lida' : 'Enviada';
        time.appendChild(check);
    }
    bubble.appendChild(time);

    return bubble;
}

function logout() {
    AuthService.logout();
}

document.getElementById('message-form')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const text = msgInput.value.trim();

    if (!text) return;
    if (!activeChat) {
        alert('Selecione um contato para enviar mensagem.');
        return;
    }

    const chat = activeChat;

    // Conectado: envia pelo WebSocket e a mensagem aparece quando o servidor a devolve
    // em /user/queue/messages. Sem conexão: usa o REST como alternativa.
    if (Realtime.connected) {
        Realtime.sendMessage(chat.id, text);
        msgInput.value = '';
        msgInput.focus();
        return;
    }

    try {
        const message = await ChatService.send(chat.id, text);

        chat.lastMessage = message;
        chat.updatedAt = message.createdAt;

        if (activeChat?.id === chat.id) {
            appendMessage(message);
        }

        msgInput.value = '';
        renderSidebar();
    } catch (error) {
        alert(error.message || 'Não foi possível enviar a mensagem.');
    }

    msgInput.focus();
});

// --- Lógica do Botão de Emoji ---
const emojiBtn = document.querySelector('button[title="Emoji"]');
const emojiContainer = document.getElementById('emoji-picker-container');

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

// --- Utilitários ---
// Conteúdo vindo da API é sempre inserido com textContent, nunca innerHTML,
// para que mensagens e nomes não possam injetar HTML/scripts na página.
function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
}

function infoMessage(text, extraClass) {
    const node = element('div', 'chat-info', text);
    if (extraClass) node.classList.add(extraClass);
    return node;
}

function findChatById(chatId) {
    return [...chatsByContactId.values()].find(chat => chat.id === chatId);
}

function otherParticipant(chat) {
    return chat.participants.find(participant => participant.id !== currentUser.id);
}

function avatarUrl(name) {
    return `https://ui-avatars.com/api/?name=${encodeURIComponent(name)}&background=random&color=fff`;
}

function formatTime(isoDate) {
    const date = new Date(isoDate);
    const time = date.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });

    if (date.toDateString() === new Date().toDateString()) {
        return time;
    }
    return `${date.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' })} ${time}`;
}

function scrollToBottom() {
    messagesContainer.scrollTop = messagesContainer.scrollHeight;
}
