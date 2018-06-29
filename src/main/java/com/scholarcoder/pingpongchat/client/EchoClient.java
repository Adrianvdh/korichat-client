package com.scholarcoder.pingpongchat.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EchoClient {
    final String hostname;
    final int port;

    protected ExecutorService executorService;
    protected BufferedReader standardInput;
    private Socket socketConnection;

    public EchoClient(String hostname, int port) {
        this.hostname = hostname;
        this.port = port;
        this.executorService = Executors.newFixedThreadPool(4);
        this.standardInput = new BufferedReader(new InputStreamReader(System.in));
    }

    public static void main(String[] args) throws IOException {
        EchoClient echoClient = new EchoClient("localhost",31145);

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
        String line;
        do {
            System.out.println("Enter something:");
            line = bufferedReader.readLine();
            String echo = echoClient.sendMessage(line);
            System.out.println(echo);
        }
        while(line != null);

    }

    public String sendMessage(String sentence) {
        if(socketConnection == null) {
            try {
                socketConnection = new Socket(hostname, port);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            PrintWriter out = new PrintWriter(socketConnection.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socketConnection.getInputStream()));

            out.println(sentence);
            String response = in.readLine();
            return response;

        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }
}
