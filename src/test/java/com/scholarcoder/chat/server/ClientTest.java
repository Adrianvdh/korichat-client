package com.scholarcoder.chat.server;

import com.scholarcoder.chat.server.user.UserRepositorySingleton;
import org.junit.*;

public class ClientTest {

    public static String HOST = "localhost";
    private static int PORT = 11359;

    Server server;

    @Before
    public void setUp() {
        server = new Server(PORT);
        server.start();

        UserRepositorySingleton.get().deleteAll();
    }

    @After
    public void tearDown() throws InterruptedException {
        server.stop();
    }

    @Test
    public void testRegisterUser() {
        String command = "REG adrian";
        String expectedResponse = "201 Created";
        Client client = new Client(HOST, PORT);

        System.out.println("Sending command");
        String response = client.sendCommand(command);
        System.out.println(response);
        System.out.println("Finished sending command");

        Assert.assertEquals(expectedResponse, response);
    }

    @Test
    public void testRegisterUserThatAlreadyExists() {
        String command = "REG adrian";
        String expectedResponse = "409 Conflict";
        Client client = new Client(HOST, PORT);

        client.sendCommand(command);
        String response = client.sendCommand(command);

        Assert.assertEquals(expectedResponse, response);
    }

    @Test
    public void testListRegisteredUsers() {
        Client client = new Client(HOST, PORT);
        client.sendCommand("REG adrian");
        client.sendCommand("REG josie");

        String command = "LISTUSER";
        String expectedResponse = "adrian,josie";

        String response = client.sendCommand(command);

        Assert.assertEquals(expectedResponse, response);
    }
}
