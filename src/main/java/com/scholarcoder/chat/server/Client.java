package com.scholarcoder.chat.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Client {
    final String hostname;
    final int port;

    protected ExecutorService executorService;
    protected BufferedReader standardInput;

    private Socket socketConnection;

    public Client(String hostname, int port) {
        this.hostname = hostname;
        this.port = port;
        this.executorService = Executors.newFixedThreadPool(4);
        this.standardInput = new BufferedReader(new InputStreamReader(System.in));

        connect();
    }

    public static void main(String[] args) {
        Client client = new Client("localhost",31145);
        client.start();
    }

    public String sendCommand(String sentence) {
        try {
            PrintWriter out = new PrintWriter(socketConnection.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socketConnection.getInputStream()));

            out.println(sentence);

            //wait for response
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                return inputLine;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return "404 Not found";
    }

    public void start() {
        connect();

        ServerListener serverListener = new ServerListener(socketConnection);
        serverListener.start();

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        String line;
        System.out.println("What's your username?");
        try {
            do {
                line = in.readLine();
                sendCommand(line);
            }
            while(line != null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void connect() {
        System.out.println("Client trying to connect");
        if(socketConnection == null) {
            try {
                socketConnection = new Socket(hostname, port);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
