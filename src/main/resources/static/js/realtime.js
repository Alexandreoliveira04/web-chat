// Conexão STOMP sobre WebSocket com o backend (/ws).
// O JWT vai no frame CONNECT; sem token válido o servidor recusa a conexão.
const Realtime = {
    client: null,

    connect(handlers) {
        const token = localStorage.getItem('webchat_token');
        const brokerURL = API_BASE_URL.replace(/^http/, 'ws').replace(/\/api\/v1$/, '/ws');

        this.client = new StompJs.Client({
            brokerURL,
            connectHeaders: { Authorization: `Bearer ${token}` },
            reconnectDelay: 5000,
            onConnect: () => {
                this.client.subscribe('/user/queue/messages', frame => handlers.onMessage(JSON.parse(frame.body)));
                this.client.subscribe('/user/queue/read', frame => handlers.onRead(JSON.parse(frame.body)));
                this.client.subscribe('/user/queue/errors', frame => handlers.onError(JSON.parse(frame.body)));
                this.client.subscribe('/topic/presence', frame => handlers.onPresence(JSON.parse(frame.body)));
                handlers.onConnect();
            },
            onStompError: frame => {
                console.error('Erro no WebSocket:', frame.headers.message);
                if ((frame.headers.message || '').includes('Autenticacao')) {
                    this.client.deactivate();
                    AuthService.logout();
                }
            }
        });

        this.client.activate();
    },

    get connected() {
        return Boolean(this.client?.connected);
    },

    sendMessage(chatId, content) {
        this.client.publish({
            destination: `/app/chats/${chatId}/messages`,
            body: JSON.stringify({ content })
        });
    }
};
