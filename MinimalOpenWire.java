import jakarta.jms.Connection;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.ObjectMessage;
import jakarta.jms.QueueBrowser;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import jakarta.jms.Topic;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.command.DestinationInfo;

import java.util.Enumeration;
import java.util.Set;

public class MinimalOpenWire {
    public static void main(String[] args) {
        // Validate command-line arguments
        if (args.length < 5 || args.length > 6) {
            System.err.println("Usage: java MinimalOpenWire <host> <port> <username> <password> <queueName> [read|readwrite|browse|write|monitoradvisory|listqueues]");
            System.exit(1);
        }

        // Parse command-line arguments
        String host = args[0];
        String port = args[1];
        String user = args[2];
        String password = args[3];
        String queueName = args[4];
        String operation = args.length == 6 ? args[5].toLowerCase() : "readwrite"; // Default to "readwrite"

        // Construct broker URL
        String brokerURL = "tcp://" + host + ":" + port;

        Connection connection = null;
        Session session = null;

        try {
            // Initialize ActiveMQ connection factory
            ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(user, password, brokerURL);
            factory.setWatchTopicAdvisories(true); // Enable advisory topic monitoring
            connection = factory.createConnection();
            connection.start();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Destination queue = session.createQueue(queueName);

            // Execute the specified operation
            switch (operation) {
                case "read":
                    readMessages(session, queue);
                    break;
                case "readwrite":
                    sendMessage(session, queue);
                    readMessages(session, queue);
                    break;
                case "browse":
                    browseQueue(session, queue);
                    break;
                case "write":
                    sendMessage(session, queue);
                    break;
                case "monitoradvisory":
                    monitorAdvisoryQueue(session);
                    break;
                case "listqueues":
                    listQueues(connection);
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
     * Sends a text message to the specified queue.
     */
    private static void sendMessage(Session session, Destination queue) throws JMSException {
        MessageProducer producer = session.createProducer(queue);
        TextMessage outMessage = session.createTextMessage("Test OpenWire Message.");
        producer.send(outMessage);
        System.out.println("Sent message: " + outMessage.getText());
    }

    /**
     * Reads a message from the specified queue with a timeout.
     */
    private static void readMessages(Session session, Destination queue) throws JMSException {
        MessageConsumer consumer = session.createConsumer(queue);
        Message inMessage = consumer.receive(5000); // 5-second timeout

        if (inMessage instanceof TextMessage) {
            TextMessage txt = (TextMessage) inMessage;
            System.out.println("Received message: " + txt.getText());
        } else {
            System.out.println("No text message received within the timeout.");
        }
    }

    /**
     * Browses messages in the queue without consuming them.
     */
    private static void browseQueue(Session session, Destination queue) throws JMSException {
        QueueBrowser browser = session.createBrowser((jakarta.jms.Queue) queue);
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
     * Lists all queues currently available in the broker.
     */
    private static void listQueues(Connection connection) throws JMSException {
        if (!(connection instanceof ActiveMQConnection)) {
            System.out.println("Connection is not an ActiveMQConnection - cannot list queues.");
            return;
        }
        ActiveMQConnection amqConnection = (ActiveMQConnection) connection;
        Set<ActiveMQQueue> queues = amqConnection.getDestinationSource().getQueues();

        System.out.println("Listing all queues in the broker...");

        int count = 0;
        if (queues != null && !queues.isEmpty()) {
            for (ActiveMQQueue queue : queues) {
                System.out.println("Queue " + ++count + ": " + queue.getQueueName());
            }
        } else {
            System.out.println("No queues found in the broker.");
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
