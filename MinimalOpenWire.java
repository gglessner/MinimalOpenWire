import jakarta.jms.Connection;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageListener;
import jakarta.jms.MessageProducer;
import jakarta.jms.ObjectMessage;
import jakarta.jms.Queue;
import jakarta.jms.QueueBrowser;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import jakarta.jms.Topic;
import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.command.DestinationInfo;
import org.apache.activemq.command.BrokerInfo;
import org.apache.activemq.transport.Transport;
import org.apache.activemq.transport.tcp.TcpTransport;

import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

public class MinimalOpenWire {
    public static void main(String[] args) {
        // Validate command-line arguments
        if (args.length < 5 || args.length > 6) {
            System.err.println("Usage: java MinimalOpenWire <host> <port> <username> <password> <destinationName> " +
                    "[readqueue|readtopic|writequeue|writetopic|readwritequeue|readwritetopic|browsequeue|" +
                    "readqueueloop|readtopicloop|browsequeueloop|monitoradvisory|listall|authenticationbypass|" +
                    "authorizationcheck|intercept|dos|infoleak|forge]");
            System.exit(1);
        }

        // Parse command-line arguments
        String host = args[0];
        String port = args[1];
        String user = args[2];
        String password = args[3];
        String destinationName = args[4];
        String operation = args.length == 6 ? args[5].toLowerCase() : "readwritequeue"; // Default operation

        // Construct broker URL
        String brokerURL = "tcp://" + host + ":" + port;

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
                    sendMessage(session, topicRW);
                    receiveMessage(session, topicRW);
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
                    testAuthenticationBypass(host, port);
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
                default:
                    System.err.println("Unknown operation: " + operation);
                    System.exit(1);
            }

        } catch (JMSException e) {
            e.printStackTrace();
        } finally {
            cleanup(connection, session);
        }
    }

    /**
     * Tests for authentication bypass using common default credentials.
     */
    private static void testAuthenticationBypass(String host, String port) {
        String[] credentials = {"admin:admin", "guest:guest", "user:password", "test:test"};
        String brokerURL = "tcp://" + host + ":" + port;

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

    /**
     * Tests authorization by attempting to send and receive from a restricted queue.
     */
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

    /**
     * Intercepts a message from the queue, modifies it, and sends it back.
     */
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

    /**
     * Sends a large number of messages to test DoS resilience.
     */
    private static void testDoS(Session session, Destination queue) throws JMSException {
        MessageProducer producer = session.createProducer(queue);
        for (int i = 0; i < 1000; i++) {
            TextMessage msg = session.createTextMessage("DoS Test " + i);
            producer.send(msg);
        }
        System.out.println("Sent 1000 messages for DoS test.");
    }

    /**
     * Detects potential information leakage by retrieving and displaying all available broker metadata.
     */
    private static void detectInfoLeakage(Connection connection) {
        if (connection instanceof ActiveMQConnection) {
            ActiveMQConnection amqConn = (ActiveMQConnection) connection;
            try {
                // --- Broker Information ---
                System.out.println("=== Broker Information ===");
                System.out.println("Broker Name: " + amqConn.getBrokerName());
            
                BrokerInfo brokerInfo = amqConn.getBrokerInfo();
                if (brokerInfo != null) {
                    System.out.println("Broker ID: " + brokerInfo.getBrokerId());
                    System.out.println("Broker URL: " + brokerInfo.getBrokerURL());
                    // Removed getBrokerVersion() as it’s not available
                
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

                // --- JMS Metadata ---
                jakarta.jms.ConnectionMetaData meta = connection.getMetaData();
                System.out.println("\n=== JMS Metadata ===");
                System.out.println("JMS Version: " + meta.getJMSVersion());
                System.out.println("JMS Major Version: " + meta.getJMSMajorVersion());
                System.out.println("JMS Minor Version: " + meta.getJMSMinorVersion());
                System.out.println("Provider Name: " + meta.getJMSProviderName());
                System.out.println("Provider Version: " + meta.getProviderVersion());
                System.out.println("Provider Major Version: " + meta.getProviderMajorVersion());
                System.out.println("Provider Minor Version: " + meta.getProviderMinorVersion());
            
                Enumeration jmsxProps = meta.getJMSXPropertyNames();
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

                // --- Transport Information ---
                System.out.println("\n=== Transport Information ===");
                Transport transport = amqConn.getTransport();
                System.out.println("Transport Type: " + transport.getClass().getName());
                System.out.println("Transport Details: " + transport.toString());
                if (transport instanceof TcpTransport) {
                    TcpTransport tcpTransport = (TcpTransport) transport;
                    System.out.println("Remote Address: " + tcpTransport.getRemoteAddress());
                }

                // --- Miscellaneous ---
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

    /**
     * Lists all queues and topics in the broker.
     */
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

    /**
     * Sends a message with forged properties.
     */
    private static void sendForgedMessage(Session session, Destination queue) throws JMSException {
        MessageProducer producer = session.createProducer(queue);
        TextMessage msg = session.createTextMessage("Forged Message");
        msg.setStringProperty("ForgedProperty", "malicious_value");
        msg.setJMSType("forgedType");
        producer.send(msg);
        System.out.println("Sent forged message with properties.");
    }

    /**
     * Sends a text message to the specified destination (queue or topic).
     */
    private static void sendMessage(Session session, Destination destination) throws JMSException {
        MessageProducer producer = session.createProducer(destination);
        TextMessage outMessage = session.createTextMessage("Test OpenWire Message.");
        producer.send(outMessage);
        System.out.println("Sent message: " + outMessage.getText());
    }

    /**
     * Reads a message from the specified destination (queue or topic) once.
     */
    private static void receiveMessage(Session session, Destination destination) throws JMSException {
        MessageConsumer consumer = session.createConsumer(destination);
        Message inMessage = consumer.receive(5000); // 5-second timeout
        if (inMessage instanceof TextMessage) {
            TextMessage txt = (TextMessage) inMessage;
            System.out.println("Received message: " + txt.getText());
        } else {
            System.out.println("No text message received within the timeout.");
        }
    }

    /**
     * Browses messages in a queue without consuming them.
     */
    private static void browseQueue(Session session, Destination queue) throws JMSException {
        QueueBrowser browser = session.createBrowser((Queue) queue);
        Enumeration<?> messages = browser.getEnumeration();
        int count = 0;
        while (messages.hasMoreElements()) {
            Message msg = (Message) messages.nextElement();
            if (msg instanceof TextMessage) {
                TextMessage txtMsg = (TextMessage) msg;
                System.out.println("Browsed message " + ++count + ": " + txtMsg.getText());
            }
        }
        if (count == 0) {
            System.out.println("No messages found in the queue.");
        }
    }

    /**
     * Continuously reads messages from a queue or topic.
     */
    private static void receiveMessageLoop(Session session, Destination destination) throws JMSException {
        MessageConsumer consumer = session.createConsumer(destination);
        consumer.setMessageListener(message -> {
            if (message instanceof TextMessage) {
                try {
                    System.out.println("Received: " + ((TextMessage) message).getText());
                } catch (JMSException e) {
                    e.printStackTrace();
                }
            }
        });
        System.out.println("Listening for messages... Press Ctrl+C to stop.");
        // Keep the program running
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Continuously browses a queue and prints new messages.
     */
    private static void browseQueueLoop(Session session, String destinationName) throws JMSException {
        Set<String> seenMessageIds = new HashSet<>();
        while (true) {
            Queue queue = session.createQueue(destinationName);
            QueueBrowser browser = session.createBrowser(queue);
            Enumeration<?> messages = browser.getEnumeration();
            while (messages.hasMoreElements()) {
                Message message = (Message) messages.nextElement();
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

    /**
     * Monitors the ActiveMQ.Advisory.Queue topic for queue-related events.
     */
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

    /**
     * Cleans up JMS resources.
     */
    private static void cleanup(Connection connection, Session session) {
        try {
            if (session != null) session.close();
            if (connection != null) connection.close();
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }
}
