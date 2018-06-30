package com.pingpongchat.server;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class EchoClientTest {

    public static String HOST = "localhost";
    private static int PORT = 11359;

    EchoServer server;

    @Before
    public void setUp() {
        server = new EchoServer(PORT);
        server.start();
    }

    @After
    public void tearDown() {
        server.stop();
    }

    @Test
    public void testEchoServerWithMessage() {
        String sentence = "Hello brown cow";
        String expectedResponse = "HELLO BROWN COW";

        EchoClient client = new EchoClient(HOST, PORT);
        String response = client.sendMessage(sentence);

        Assert.assertEquals(response, expectedResponse);
    }
}
