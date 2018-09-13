package org.korichat.client;

import org.korichat.messaging.AckMessage;
import org.korichat.messaging.Callback;
import org.korichat.messaging.Message;
import org.korichat.messaging.protocol.FetchRequest;
import org.korichat.messaging.protocol.FetchResponse;
import org.korichat.messaging.protocol.Initialize;
import org.korichat.messaging.protocol.Subscribe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class MessagingClient {
    private Logger logger = LoggerFactory.getLogger(MessagingClient.class);

    final String hostname;
    final int port;

    private Socket socketConnection;
    private ObjectOutputStream outputStream;
    private ObjectInputStream inputStream;

    private ExecutorService executorService = Executors.newCachedThreadPool();

    private Consumer<AckMessage> onConnect;

    private List<String> unAckedMessageIds = new ArrayList<>();
    private List<String> subscribedTopics = new ArrayList<>();

    public MessagingClient(String hostname, int port) {
        this.hostname = hostname;
        this.port = port;
    }

    /**
     * try connect the client to the server using the host and port specified in the constructor.
     * <p>
     * If the onConnect callback is registered before calling this method, it will be invoked
     * once a connection has been established with the server.
     * <p>
     * This method blocks while waiting for a server acknowledgement.
     */
    public void connect() {
        logger.info("Client trying to connect to server {}:{}", hostname, port);
        if (socketConnection == null) {
            trySetupInAndOutStreams();

            Message<Initialize> initializeMessage = new Message<>(new Initialize(), null);
            apply(initializeMessage);
            unAckedMessageIds.add(initializeMessage.getIdentifier());

            Message<AckMessage> message = null;
            try {
                message = waitForServerResponse();
            } catch (IOException e) {
                logger.error("Failed to get response from server!", e);
            }

            String initializeMessageId = unAckedMessageIds.get(0);
            String ackId = message.getPayload().getIdentifier();
            if (initializeMessageId.equals(ackId) && onConnect != null) {
                onConnect.accept(message.getPayload());
            }
            unAckedMessageIds.remove(ackId);
        } else {
            logger.warn("Please disconnect first!");
        }
    }

    private <T> Message<T> waitForServerResponse() throws IOException {
        while (!Thread.currentThread().isInterrupted()) {
            Message<T> message = null;
            try {
                message = (Message) inputStream.readObject();
                if (message == null) {
                    break;
                }
            } catch (EOFException e) {
                break;
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            return message;
        }
        return null;
    }


    /**
     * Registers an consumer that is called when the client has connected to the server.
     * This method blocks until the server responds.
     */
    public void onConnect(Consumer<AckMessage> onConnect) {
        this.onConnect = onConnect;
    }

    /**
     * Send a message asynchronously out with a configured topic. The server should
     * return with an acknowledgment.
     *
     * @param message message to send
     * @return
     */
    public Future<AckMessage> send(Message message) {
        apply(message);
        unAckedMessageIds.add(message.getIdentifier());

        return executorService.submit(waitForAck());
    }

    private Callable<AckMessage> waitForAck() {
        return () -> {
            Message<AckMessage> ackMessage = null;
            try {
                ackMessage = waitForServerResponse();
            } catch (IOException e) {
                e.printStackTrace();
            }

            unAckedMessageIds.remove(ackMessage.getIdentifier());
            return ackMessage.getPayload();
        };
    }

    private <T> Future<T> sendAwaitKnowType(Message message) {
        apply(message);
        unAckedMessageIds.add(message.getIdentifier());

        Callable<T> task = waitForKnowType();
        return executorService.submit(task);
    }


    private <T> Callable<T> waitForKnowType() {
        return () -> {
            Message<T> ackMessage = null;
            try {
                ackMessage = waitForServerResponse();
            } catch (IOException e) {
                e.printStackTrace();
            }

            unAckedMessageIds.remove(ackMessage.getIdentifier());
            return ackMessage.getPayload();
        };
    }

    /**
     * @param message
     * @param callback
     * @return
     */
    public Future<AckMessage> send(Message message, Callback callback) {
        apply(message);
        unAckedMessageIds.add(message.getIdentifier());

        return executorService.submit(() -> {
            Message response = null;
            try {
                response = waitForServerResponse();
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (callback != null) {
                if (response.getPayloadType() == AckMessage.class) {
                    AckMessage ack = (AckMessage) response.getPayload();
                    callback.onAck(ack);
                    unAckedMessageIds.remove(ack.getIdentifier());
                    return ack;
                }
                if (response.getPayloadType() == Throwable.class) {
                    callback.onError((Throwable) response.getPayload());
                    unAckedMessageIds.remove(message.getIdentifier());
                }
            }
            return null;
        });
    }

    /**
     * Allows the client to subscribe to certain topics on the message broker.
     * The server may reject the client from subscribing to certain topics.
     * <p>
     * Only tenant aware and public topics may be subscribed to.
     *
     * @param topic
     */
    public void subscribe(String topic) {
        Message<Subscribe> subscribeMessage = new Message<>(new Subscribe(topic), null);
        try {
            // handle synchronously
            send(subscribeMessage, new Callback() {
                @Override
                public void onAck(AckMessage ackMessage) {
                    subscribedTopics.add(topic);
                }

                @Override
                public void onError(Throwable throwable) {
                    String reason = throwable.getMessage();
                    throw new ClientException(String.format("Unable to subscribe to the '%s'! Reason: %s", topic, reason));
                }
            }).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    /**
     * Poll the message broker for any messages on all the topics the client
     * is subscribed to.
     *
     * @param timeoutMillis
     * @return
     */
    public List<Message> poll(int timeoutMillis) {
        long start = System.currentTimeMillis();
        long remaining;
        List<Message> messages = new ArrayList<>();
        boolean complete;
        do {
            for (String topic : subscribedTopics) {
                try {
                    // request to get messages for subscribed topic
                    Message<FetchRequest> fetchRequest = new Message(new FetchRequest(topic), null);
                    Future<FetchResponse> future = sendAwaitKnowType(fetchRequest);
                    FetchResponse fetchResponse = future.get();

                    Integer currentTopicMessagesCount = fetchResponse.getTopicSize();

                    for (int i = 0; i < currentTopicMessagesCount; i++) {
                        try {
                            Message message = waitForServerResponse();
                            messages.add(message);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
            }
            complete = true;

            long elapsed = System.currentTimeMillis() - start;
            remaining = timeoutMillis - elapsed;
        } while (remaining > 0 && !complete);


        return messages;
    }

    private void apply(Message<Initialize> initializeMessage) {
        try {
            this.outputStream.writeObject(initializeMessage);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void trySetupInAndOutStreams() {
        try {
            this.socketConnection = new Socket(hostname, port);
            this.outputStream = new ObjectOutputStream(socketConnection.getOutputStream());
            this.inputStream = new ObjectInputStream(socketConnection.getInputStream());
        } catch (ConnectException e) {
            throw new ClientException(String.format("An error occurred when connecting to the server! %s", e.getMessage()));
        } catch (IOException e) {
            throw new ClientException(String.format("An error occurred! %s", e.getMessage()));
        }
    }

    /**
     * Ends the network session with the server
     */
    public void disconnect() {
        logger.info("Client trying to disconnect from server");
        if (socketConnection == null) {
            logger.warn("Could not disconnect because the client isn't connected yet!");
            return;
        }

        // send disconnect

        try {
            this.outputStream.close();
            this.inputStream.close();
            this.socketConnection.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        socketConnection = null;
    }
}
