import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer {

    private final static ObjectMapper objectMapper = new ObjectMapper();

    // Socket - абстракция, к которой можно подключиться
    // ip-address + port - socket
    // network - сеть - набор соединенных устройств
    // ip-address - это адрес устройства в какой-то сети
    // 8080 - http
    // 443 - https
    // 35 - smtp
    // 21 - ftp
    // 5432 - стандартный порт postgres
    // клиент подключается к серверу

    /**
     * Порядок взаимодействия:
     * 1. Клиент подключается к серверу
     * 2. Клиент посылает сообщение, в котором указан логин. Если на сервере уже есть подключеный клиент с таким логином, то соедение разрывается
     * 3. Клиент может посылать 3 типа команд:
     * 3.1 list - получить логины других клиентов
     * <p>
     * 3.2 send @login message - отправить личное сообщение с содержимым message другому клиенту с логином login
     * 3.3 send message - отправить сообщение всем с содержимым message
     */

    // 1324.132.12.3:8888
    public static void main(String[] args) {
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
        try (ServerSocket server = new ServerSocket(8888)) {
            System.out.println("Сервер запущен");

            while (true) {
                System.out.println("Ждем клиентского подключения");
                Socket client = server.accept();
                ClientHandler clientHandler = new ClientHandler(client, clients);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            System.err.println("Ошибка во время работы сервера: " + e.getMessage());
        }
    }

    private static class ClientHandler implements Runnable {

        private final Socket client;
        private final Scanner in;
        private final PrintWriter out;
        private final Map<String, ClientHandler> clients;
        private String clientLogin;

        public ClientHandler(Socket client, Map<String, ClientHandler> clients) throws IOException {
            this.client = client;
            this.clients = clients;

            this.in = new Scanner(client.getInputStream());
            this.out = new PrintWriter(client.getOutputStream(), true);
        }

        @Override
        public void run() {
            System.out.println("Подключен новый клиент");

            try {
                String loginRequest = in.nextLine();
                LoginRequest request = objectMapper.reader().readValue(loginRequest, LoginRequest.class);
                this.clientLogin = request.getLogin();
            } catch (IOException e) {
                System.err.println("Не удалось прочитать сообщение от клиента [" + clientLogin + "]: " + e.getMessage());
                String unsuccessfulResponse = createLoginResponse(false);
                out.println(unsuccessfulResponse);
                doClose();
                return;
            }

            System.out.println("Запрос от клиента: " + clientLogin);
            // Проверка, что логин не занят
            if (clients.containsKey(clientLogin)) {
                String unsuccessfulResponse = createLoginResponse(false);
                out.println(unsuccessfulResponse);
                doClose();
                return;
            }

            clients.put(clientLogin, this);
            String successfulLoginResponse = createLoginResponse(true);
            out.println(successfulLoginResponse);

            while (true) {
                String msgFromClient = in.nextLine();

                final String type;
                try {
                    AbstractRequest request = objectMapper.reader().readValue(msgFromClient, AbstractRequest.class);
                    type = request.getType();
                    if (SendMessageRequest.TYPE.equals(type)) {

                        final SendMessageRequest request1;
                        request1 = objectMapper.reader().readValue(msgFromClient, SendMessageRequest.class);

                        ClientHandler clientTo = clients.get(request1.getRecipient());
                        if (clientTo == null) {
                            sendMessage("Клиент с логином [" + request1.getRecipient() + "] не найден");
                            continue;
                        }
                        clientTo.sendMessage(request1.getMessage());
                    } else if (SendBroadcastMessageRequest.TYPE.equals(type)) { // BroadcastRequest.TYPE.equals(type)
                        final SendBroadcastMessageRequest request2;

                        request2 = objectMapper.reader().readValue(msgFromClient, SendBroadcastMessageRequest.class);

                        if (clients.size() > 1) {
                            for (String clientName : clients.keySet()) {
                                if (!clientName.equals(clientLogin)) {
                                    ClientHandler clientTo = clients.get(clientName);
                                    clientTo.sendMessage(request2.getMessage());
                                }
                            }
                        }

                    } else if (SendToGetUsersRequest.TYPE.equals(type)) {
                        StringBuilder strUsers = new StringBuilder();
                        int count = 0;
                        for (String clientName : clients.keySet()) {
                            if (!clientName.equals(clientLogin)) {
                                strUsers.append(clientName + " ");
                                count++;
                            }
                        }
                        if (count == 0) {
                            sendMessage("Вы единственный абонент в чате)");
                        } else {
                            sendMessage("Абоненты в чате: " + strUsers);
                        }

                    } else if (SendDisconnectRequest.TYPE.equals(type)) {
                        final SendDisconnectRequest request4;
                        for (String clientName : clients.keySet()) {
                            if (!clientName.equals(clientLogin)) {
                                ClientHandler clientTo = clients.get(clientName);
                                clientTo.sendMessage(clientLogin + " отключился от сервера");
                            }
                        }
                        System.out.println(SendDisconnectRequest.TYPE);
                        break;
                    } else {
                        System.err.println("Неизвестный тип сообщения: " + type);
                        sendMessage("Неизвестный тип сообщения: " + type);
                        continue;
                    }

                } catch (IOException e) {
                    System.err.println("Не удалось прочитать сообщение от клиента [" + clientLogin + "]: " + e.getMessage());
                    sendMessage("Не удалось прочитать сообщение: " + e.getMessage());
                    continue;
                }

            }
            doClose();
        }

        private void doClose() {
            try {
                in.close();
                out.close();
                client.close();
            } catch (IOException e) {
                System.err.println("Ошибка во время отключения клиента: " + e.getMessage());
            }
        }

        public void sendMessage(String message) {

            out.println(message);
        }

        private String createLoginResponse(boolean success) {
            LoginResponse loginResponse = new LoginResponse();
            loginResponse.setConnected(success);
            try {
                return objectMapper.writer().writeValueAsString(loginResponse);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Не удалось создать loginResponse: " + e.getMessage());
            }
        }
    }
}
