package com.scholarcoder.pingpongchat.client;

import com.concurrenctchat.EchoServer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Scanner;

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

    @Test
    public void testEchoServerUsingInputStreams() throws IOException {
        //write to standard input.
        //get response back from server output

        String sentence = "Hello brown cow";
        String expectedResponse = "HELLO BROWN COW";

        EchoClient client = new EchoClient(HOST, PORT);
        BufferedReader bufferedReaderMock = Mockito.mock(BufferedReader.class);
        Mockito.when(bufferedReaderMock.readLine()).thenReturn(sentence);

        client.listen();

//        Assert.assertEquals(response, expectedResponse);
    }

    @Test
    public void shouldTakeUserInput() {
        Scanner scanner = new Scanner(System.in);
        String userInput = scanner.nextLine();
//
//        String input = "add 5";
//        InputStream in = new ByteArrayInputStream(input.getBytes());
//        System.setIn(in);
//
//        Assert.assertEquals(input, userInput);
    }
}
