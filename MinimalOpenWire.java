import jakarta.jms.*;
import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.command.ActiveMQTopic;
import org.apache.activemq.command.BrokerInfo;
import org.apache.activemq.command.DestinationInfo;
import org.apache.activemq.transport.Transport;
import org.apache.activemq.transport.tcp.TcpTransport;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MinimalOpenWire {
    public static void main(String[] args) {
        // Validate command-line arguments
        if (args.length < 6 || args.length > 7) {
            System.err.println("Usage: java MinimalOpenWire <method> <host> <port> <username> <password> <destinationName> " +
                    "[readqueue|readtopic|writequeue|writetopic|readwritequeue|readwritetopic|browsequeue|" +
                    "readqueueloop|readtopicloop|browsequeueloop|monitoradvisory|listall|authenticationbypass|" +
                    "authorizationcheck|intercept|dos|infoleak|forge|deletequeue|deletetopic]");
            System.exit(1);
        }

        // Parse command-line arguments
        String conntransport = args[0];
        String host = args[1];
        String port = args[2];
        String user = args[3];
        String password = args[4];
        String destinationName = args[5];
        String operation = args.length == 7 ? args[6].toLowerCase() : "readwritequeue"; // Default operation

        // Construct broker URL
        String brokerURL = conntransport + "://" + host + ":" + port;

        Connection connection = null;
        Session session = null;

        try {
            // Initialize ActiveMQ connection factory
            ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerURL);
            factory.setUserName(user);
            factory.setPassword(password);
            factory.setWatchTopicAdvisories(true); // Enable advisory topic monitoring
            connection = factory.createConnection();
            connection.start();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            // Execute the specified operation
            switch (operation) {
                case "readqueue":
                    Destination queueRead = session.createQueue(destinationName);
                    receiveMessage(session, queueRead);
                    break;
                case "readtopic":
                    Destination topicRead = session.createTopic(destinationName);
                    receiveMessage(session, topicRead);
                    break;
                case "writequeue":
                    Destination queueWrite = session.createQueue(destinationName);
                    sendMessage(session, queueWrite);
                    break;
                case "writetopic":
                    Destination topicWrite = session.createTopic(destinationName);
                    sendMessage(session, topicWrite);
                    break;
                case "readwritequeue":
                    Destination queueRW = session.createQueue(destinationName);
                    sendMessage(session, queueRW);
                    receiveMessage(session, queueRW);
                    break;
                case "readwritetopic":
                    Destination topicRW = session.createTopic(destinationName);
                    readWriteTopic(session, topicRW);
                    break;
                case "browsequeue":
                    Destination queueBrowse = session.createQueue(destinationName);
                    browseQueue(session, queueBrowse);
                    break;
                case "readqueueloop":
                    Destination queueLoop = session.createQueue(destinationName);
                    receiveMessageLoop(session, queueLoop);
                    break;
                case "readtopicloop":
                    Destination topicLoop = session.createTopic(destinationName);
                    receiveMessageLoop(session, topicLoop);
                    break;
                case "browsequeueloop":
                    browseQueueLoop(session, destinationName);
                    break;
                case "monitoradvisory":
                    monitorAdvisoryQueue(session);
                    break;
                case "listall":
                    listQueuesAndTopics(connection);
                    break;
                case "authenticationbypass":
                    testAuthenticationBypass(conntransport, host, port);
                    break;
                case "authorizationcheck":
                    testAuthorization(session);
                    break;
                case "intercept":
                    Destination queueIntercept = session.createQueue(destinationName);
                    interceptAndModify(session, queueIntercept);
                    break;
                case "dos":
                    Destination queueDoS = session.createQueue(destinationName);
                    testDoS(session, queueDoS);
                    break;
                case "infoleak":
                    detectInfoLeakage(connection);
                    break;
                case "forge":
                    sendForgedMessage(session, session.createQueue(destinationName));
                    break;
                case "deletequeue":
                    if (connection instanceof ActiveMQConnection) {
                        ActiveMQConnection amqConn = (ActiveMQConnection) connection;
                        ActiveMQQueue queueToDelete = new ActiveMQQueue(destinationName);
                        try {
                            amqConn.destroyDestination(queueToDelete);
                            System.out.println("Queue deleted: " + destinationName);
                        } catch (JMSException e) {
                            System.out.println("Failed to delete queue: " + e.getMessage());
                        }
                    } else {
                        System.out.println("Connection is not an ActiveMQConnection.");
                    }
                    break;
                case "deletetopic":
                    if (connection instanceof ActiveMQConnection) {
                        ActiveMQConnection amqConn = (ActiveMQConnection) connection;
                        ActiveMQTopic topicToDelete = new ActiveMQTopic(destinationName);
                        try {
                            amqConn.destroyDestination(topicToDelete);
                            System.out.println("Topic deleted: " + destinationName);
                        } catch (JMSException e) {
                            System.out.println("Failed to delete topic: " + e.getMessage());
                        }
                    } else {
                        System.out.println("Connection is not an ActiveMQConnection.");
                    }
                    break;
                default:
                    System.err.println("Unknown operation: " + operation);
                    System.exit(1);
            }

        } catch (JMSException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            cleanup(connection, session);
        }
    }

    // Sending a Message
    private static void sendMessage(Session session, Destination destination) throws JMSException {
        MessageProducer producer = session.createProducer(destination);
        TextMessage message = session.createTextMessage("Test OpenWire Message");
        producer.send(message);
        System.out.println("Sent: " + message.getText());
        producer.close();
    }

    // Receiving a Message
    private static void receiveMessage(Session session, Destination destination) throws JMSException {
        MessageConsumer consumer = session.createConsumer(destination);
        Message message = consumer.receive(5000); // 5-second timeout
        if (message instanceof TextMessage) {
            System.out.println("Received: " + ((TextMessage) message).getText());
        } else {
            System.out.println("No message received within 5 seconds.");
        }
        consumer.close();
    }

    // Reading and Writing to a Topic
    private static void readWriteTopic(Session session, Destination destination) throws JMSException, InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        MessageConsumer consumer = session.createConsumer(destination);
        consumer.setMessageListener(message -> {
            try {
                if (message instanceof TextMessage) {
                    System.out.println("Received: " + ((TextMessage) message).getText());
                    latch.countDown();
                }
            } catch (JMSException e) {
                e.printStackTrace();
            }
        });

        MessageProducer producer = session.createProducer(destination);
        TextMessage outMessage = session.createTextMessage("Test OpenWire Topic Message");
        producer.send(outMessage);
        System.out.println("Sent: " + outMessage.getText());

        if (latch.await(5, TimeUnit.SECONDS)) {
            System.out.println("Message received successfully.");
        } else {
            System.out.println("No message received within 5 seconds.");
        }

        consumer.close();
        producer.close();
    }

    // Receiving Messages in a Loop
    private static void receiveMessageLoop(Session session, Destination destination) throws JMSException {
        MessageConsumer consumer = session.createConsumer(destination);
        consumer.setMessageListener(message -> {
            try {
                if (message instanceof TextMessage) {
                    System.out.println("Received: " + ((TextMessage) message).getText());
                }
            } catch (JMSException e) {
                e.printStackTrace();
            }
        });
        System.out.println("Listening for messages on " + destination + "... Press Ctrl+C to stop.");
        // Keep the program running
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Browsing a Queue
    private static void browseQueue(Session session, Destination queue) throws JMSException {
        QueueBrowser browser = session.createBrowser((Queue) queue);
        Enumeration<Message> messages = castEnumeration(browser.getEnumeration());
        int count = 0;
        while (messages.hasMoreElements()) {
            Message msg = messages.nextElement();
            if (msg instanceof TextMessage) {
                TextMessage txtMsg = (TextMessage) msg;
                System.out.println("Browsed message " + ++count + ": " + txtMsg.getText());
            }
        }
        if (count == 0) {
            System.out.println("No messages found in the queue.");
        }
    }

    // Browsing a Queue in a Loop
    private static void browseQueueLoop(Session session, String destinationName) throws JMSException {
        Set<String> seenMessageIds = new HashSet<>();
        while (true) {
            Queue queue = session.createQueue(destinationName);
            QueueBrowser browser = session.createBrowser(queue);
            Enumeration<Message> messages = castEnumeration(browser.getEnumeration());
            while (messages.hasMoreElements()) {
                Message message = messages.nextElement();
                String messageId = message.getJMSMessageID();
                if (!seenMessageIds.contains(messageId)) {
                    seenMessageIds.add(messageId);
                    if (message instanceof TextMessage) {
                        System.out.println("Browsed new message: " + ((TextMessage) message).getText());
                    }
                }
            }
            try {
                Thread.sleep(1000); // Check every second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    // Monitoring Advisory Queue
    private static void monitorAdvisoryQueue(Session session) throws JMSException {
        Topic advisoryTopic = session.createTopic("ActiveMQ.Advisory.Queue");
        MessageConsumer consumer = session.createConsumer(advisoryTopic);

        System.out.println("Monitoring ActiveMQ.Advisory.Queue for messages...");
        int count = 0;
        while (true) {
            Message msg = consumer.receive(1000); // 1-second timeout
            if (msg != null) {
                if (msg instanceof ObjectMessage) {
                    ObjectMessage objMsg = (ObjectMessage) msg;
                    Object obj = objMsg.getObject();
                    if (obj instanceof DestinationInfo) {
                        DestinationInfo destInfo = (DestinationInfo) obj;
                        String operationType = destInfo.getOperationType() == DestinationInfo.ADD_OPERATION_TYPE ? "ADDED" : "REMOVED";
                        System.out.println("Advisory message " + ++count + ": Queue " + destInfo.getDestination().getPhysicalName() + " " + operationType);
                    } else {
                        System.out.println("Advisory message " + ++count + ": Unknown advisory object: " + obj);
                    }
                } else {
                    System.out.println("Advisory message " + ++count + ": Non-object message received: " + msg);
                }
            } else if (count == 0) {
                System.out.println("No advisory messages received yet.");
                count = -1; // Prevent repeated "no messages" output
            }
            try {
                Thread.sleep(100); // Reduce CPU usage
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    // Listing All Queues and Topics
    private static void listQueuesAndTopics(Connection connection) throws JMSException {
        if (connection instanceof ActiveMQConnection) {
            ActiveMQConnection amqConn = (ActiveMQConnection) connection;
            Set<ActiveMQQueue> queues = amqConn.getDestinationSource().getQueues();
            System.out.println("Queues:");
            int qCount = 0;
            for (ActiveMQQueue q : queues) {
                System.out.println("Queue " + ++qCount + ": " + q.getQueueName());
            }
            Set<?> topics = amqConn.getDestinationSource().getTopics();
            System.out.println("Topics:");
            int tCount = 0;
            for (Object t : topics) {
                System.out.println("Topic " + ++tCount + ": " + t);
            }
        } else {
            System.out.println("Connection is not an ActiveMQConnection.");
        }
    }

    // Testing Authentication Bypass
    private static void testAuthenticationBypass(String conntransport, String host, String port) {
        String[] credentials = {"admin:admin", "guest:guest", "user:password", "test:test"};
        String brokerURL = conntransport + "://" + host + ":" + port;

        for (String cred : credentials) {
            String[] parts = cred.split(":");
            if (parts.length != 2) continue;
            String user = parts[0];
            String password = parts[1];
            try {
                ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerURL);
                factory.setUserName(user);
                factory.setPassword(password);
                Connection conn = factory.createConnection();
                conn.start();
                System.out.println("Authentication bypass succeeded with: " + user + ":" + password);
                conn.close();
            } catch (JMSException e) {
                System.out.println("Authentication failed with: " + user + ":" + password + " - " + e.getMessage());
            }
        }
    }

    // Testing Authorization
    private static void testAuthorization(Session session) throws JMSException {
        Destination restrictedQueue = session.createQueue("restricted.test");

        // Try to send a message
        try {
            MessageProducer producer = session.createProducer(restrictedQueue);
            TextMessage msg = session.createTextMessage("Authorization Test");
            producer.send(msg);
            System.out.println("Successfully sent to restricted queue: restricted.test");
        } catch (JMSException e) {
            System.out.println("Failed to send to restricted queue: " + e.getMessage());
        }

        // Try to consume a message
        try {
            MessageConsumer consumer = session.createConsumer(restrictedQueue);
            Message inMessage = consumer.receive(1000);
            if (inMessage != null) {
                System.out.println("Successfully consumed from restricted queue.");
            } else {
                System.out.println("No message found in restricted queue.");
            }
        } catch (JMSException e) {
            System.out.println("Failed to consume from restricted queue: " + e.getMessage());
        }
    }

    // Intercepting and Modifying Messages
    private static void interceptAndModify(Session session, Destination queue) throws JMSException {
        MessageConsumer consumer = session.createConsumer(queue);
        Message inMessage = consumer.receive(5000);
        if (inMessage instanceof TextMessage) {
            TextMessage txtMsg = (TextMessage) inMessage;
            String original = txtMsg.getText();
            String modifiedText = original + " [MODIFIED]";
            TextMessage modifiedMsg = session.createTextMessage(modifiedText);
            MessageProducer producer = session.createProducer(queue);
            producer.send(modifiedMsg);
            System.out.println("Intercepted: " + original + " | Sent: " + modifiedText);
        } else {
            System.out.println("No text message to intercept within the timeout.");
        }
    }

    // Testing Denial of Service
    private static void testDoS(Session session, Destination queue) throws JMSException {
        MessageProducer producer = session.createProducer(queue);
        for (int i = 0; i < 1000; i++) {
            TextMessage msg = session.createTextMessage("DoS Test " + i);
            producer.send(msg);
        }
        System.out.println("Sent 1000 messages for DoS test.");
    }

    // Detecting Information Leakage
    private static void detectInfoLeakage(Connection connection) {
        if (connection instanceof ActiveMQConnection) {
            ActiveMQConnection amqConn = (ActiveMQConnection) connection;
            try {
                // Broker Information
                System.out.println("=== Broker Information ===");
                System.out.println("Broker Name: " + amqConn.getBrokerName());
                
                BrokerInfo brokerInfo = amqConn.getBrokerInfo();
                if (brokerInfo != null) {
                    System.out.println("Broker ID: " + brokerInfo.getBrokerId());
                    System.out.println("Broker URL: " + brokerInfo.getBrokerURL());
                    
                    BrokerInfo[] peers = brokerInfo.getPeerBrokerInfos();
                    if (peers != null && peers.length > 0) {
                        System.out.println("Peer Brokers:");
                        for (BrokerInfo peer : peers) {
                            System.out.println("  - Name: " + peer.getBrokerName() + 
                                               ", ID: " + peer.getBrokerId());
                        }
                    } else {
                        System.out.println("Peer Brokers: None");
                    }
                } else {
                    System.out.println("BrokerInfo unavailable");
                }

                // JMS Metadata
                jakarta.jms.ConnectionMetaData meta = connection.getMetaData();
                System.out.println("\n=== JMS Metadata ===");
                System.out.println("JMS Version: " + meta.getJMSVersion());
                System.out.println("JMS Major Version: " + meta.getJMSMajorVersion());
                System.out.println("JMS Minor Version: " + meta.getJMSMinorVersion());
                System.out.println("Provider Name: " + meta.getJMSProviderName());
                System.out.println("Provider Version: " + meta.getProviderVersion());
                System.out.println("Provider Major Version: " + meta.getProviderMajorVersion());
                System.out.println("Provider Minor Version: " + meta.getProviderMinorVersion());
                
                Enumeration<String> jmsxProps = castEnumeration(meta.getJMSXPropertyNames());
                System.out.print("Supported JMSX Properties: ");
                if (jmsxProps.hasMoreElements()) {
                    StringBuilder props = new StringBuilder();
                    while (jmsxProps.hasMoreElements()) {
                        props.append(jmsxProps.nextElement()).append(", ");
                    }
                    System.out.println(props.substring(0, props.length() - 2));
                } else {
                    System.out.println("None");
                }

                // Transport Information
                System.out.println("\n=== Transport Information ===");
                Transport transport = amqConn.getTransport();
                System.out.println("Transport Type: " + transport.getClass().getName());
                System.out.println("Transport Details: " + transport.toString());
                if (transport instanceof TcpTransport) {
                    TcpTransport tcpTransport = (TcpTransport) transport;
                    System.out.println("Remote Address: " + tcpTransport.getRemoteAddress());
                }

                // Miscellaneous
                System.out.println("\n=== Miscellaneous ===");
                System.out.println("Client ID: " + (amqConn.getClientID() != null ? amqConn.getClientID() : "Not set"));
                System.out.println("Connection Started: " + amqConn.isStarted());
                System.out.println("Connection Closed: " + amqConn.isClosed());

            } catch (JMSException e) {
                System.out.println("Failed to retrieve broker info: " + e.getMessage());
            } catch (Exception e) {
                System.out.println("Unexpected error: " + e.getMessage());
            }
        } else {
            System.out.println("Connection is not an ActiveMQConnection.");
        }
    }

    // Sending a Forged Message
    private static void sendForgedMessage(Session session, Destination queue) throws JMSException {
        MessageProducer producer = session.createProducer(queue);
        TextMessage msg = session.createTextMessage("Forged Message");
        msg.setStringProperty("ForgedProperty", "malicious_value");
        msg.setJMSType("forgedType");
        producer.send(msg);
        System.out.println("Sent forged message with properties.");
    }

    // Cleaning Up Resources
    private static void cleanup(Connection connection, Session session) {
        try {
            if (session != null) session.close();
            if (connection != null) connection.close();
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }

    // Utility Method for Casting Enumeration
    @SuppressWarnings("unchecked")
    private static <T> Enumeration<T> castEnumeration(Enumeration raw) {
        return (Enumeration<T>) raw;
    }
}
