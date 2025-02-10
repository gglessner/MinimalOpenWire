import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.QueueBrowser;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;

import java.util.Enumeration;

public class MinimalOpenWire {
    public static void main(String[] args) {
        if (args.length < 5 || args.length > 6) {
            System.err.println("Usage: java MinimalOpenWire <host> <port> <username> <password> <queueName> [read|readwrite|browse|write]");
            System.exit(1);
        }

        // Parse command-line arguments
        String host = args[0];
        String port = args[1];
        String user = args[2];
        String password = args[3];
        String queueName = args[4];
        String operation = args.length == 6 ? args[5].toLowerCase() : "readwrite"; // Default to readwrite if not specified

        String brokerURL = "tcp://" + host + ":" + port;

        Connection connection = null;
        Session session = null;

        try {
            ConnectionFactory factory = new ActiveMQConnectionFactory(user, password, brokerURL);
            connection = factory.createConnection();
            connection.start();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Destination queue = session.createQueue(queueName);

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

    private static void sendMessage(Session session, Destination queue) throws JMSException {
        MessageProducer producer = session.createProducer(queue);
        TextMessage outMessage = session.createTextMessage("Test OpenWire Message.");
        producer.send(outMessage);
        System.out.println("Sent message: " + outMessage.getText());
    }

    private static void readMessages(Session session, Destination queue) throws JMSException {
        MessageConsumer consumer = session.createConsumer(queue);
        Message inMessage = consumer.receive(5000);  // wait up to 5 seconds

        if (inMessage instanceof TextMessage) {
            TextMessage txt = (TextMessage) inMessage;
            System.out.println("Received message: " + txt.getText());
        } else {
            System.out.println("No text message received within the timeout.");
        }
    }

    private static void browseQueue(Session session, Destination queue) throws JMSException {
        QueueBrowser browser = session.createBrowser((jakarta.jms.Queue) queue);
        Enumeration messages = browser.getEnumeration();
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

    private static void cleanup(Connection connection, Session session) {
        try {
            if (session != null) session.close();
            if (connection != null) connection.close();
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }
}
