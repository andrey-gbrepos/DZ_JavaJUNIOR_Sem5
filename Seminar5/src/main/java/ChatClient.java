import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.*;


public class ChatClient {

    private static ObjectMapper objectMapper = new ObjectMapper();
    static Boolean threadInCondition = true;

    public static void main(String[] args) {

        Scanner console = new Scanner(System.in);
        String clientLogin = console.nextLine();
        String sendExit = "";

        try (Socket server = new Socket("localhost", 8888)) {
            System.out.println("Успешно подключились к серверу");

            try (PrintWriter out = new PrintWriter(server.getOutputStream(), true)) {
                Scanner in = new Scanner(server.getInputStream());
                String loginRequest = createLoginRequest(clientLogin);
                out.println(loginRequest);

                String loginResponseString = in.nextLine();
                if (!checkLoginResponse(loginResponseString)) {
                    System.out.println("Не удалось подключиться к серверу");
                    return;
                }

                new Thread(() -> {
                    while (threadInCondition) {
                        String msgFromServer = in.nextLine();
                        System.out.println("Сообщение от сервера: " + msgFromServer);
                    }
                }).start();


                while (true) {
                    System.out.println("Что хочу сделать?");
                    System.out.println("1. Послать сообщение другу");
                    System.out.println("2. Послать сообщение всем");
                    System.out.println("3. Получить список логинов");
                    System.out.println("4. Выйти из чата");

                    String type = console.nextLine();

                    if (type.equals("1")) {
                        SendMessageRequest request = new SendMessageRequest();
                        System.out.print("Введите логин получателя сообщения : ");
                        request.setRecipient(console.nextLine());
                        System.out.print("\n" + "Введите текст сообщения: ");
                        request.setMessage(console.nextLine());
                        String sendMsgRequest = objectMapper.writeValueAsString(request);
                        out.println(sendMsgRequest);

                    } else if (type.equals("2")) {
                        // TODO: Создаете запрос отправки "всем"
                        SendBroadcastMessageRequest request = new SendBroadcastMessageRequest();
                        System.out.print("Ваше сообщение для всех: ");
                        request.setMessage(console.nextLine());
                        String sendBroadcastMsgRequest = objectMapper.writeValueAsString(request);
                        out.println(sendBroadcastMsgRequest);

                    } else if (type.equals("3")) {
                        SendToGetUsersRequest request = new SendToGetUsersRequest();
                        String sendToGetUsersRequest = objectMapper.writeValueAsString(request);
                        System.out.println(sendToGetUsersRequest);
                        System.out.println("Список абонентов в чате: ");
                        out.println(sendToGetUsersRequest);

                    } else if (type.equals("4")) {
                        threadInCondition = false;
                        SendDisconnectRequest request = new SendDisconnectRequest();
                        sendExit = objectMapper.writeValueAsString(request);
                        break;

                    } else {
                        System.out.println("Неизвестная команда, повторите ввод");
                    }
                }
                out.println(sendExit);
            }
        } catch (IOException e) {
            System.err.println("Ошибка во время подключения к серверу: " + e.getMessage());
        }
        System.out.println("Отключились от сервера");
    }

    private static String createLoginRequest(String login) {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setLogin(login);

        try {
            return objectMapper.writeValueAsString(loginRequest);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Ошибка JSON: " + e.getMessage());
        }
    }

    private static boolean checkLoginResponse(String loginResponse) {
        try {
            LoginResponse resp = objectMapper.reader().readValue(loginResponse, LoginResponse.class);
            return resp.isConnected();
        } catch (IOException e) {
            System.err.println("Ошибка чтения JSON: " + e.getMessage());
            return false;
        }
    }
}