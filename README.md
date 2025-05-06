# MinimalOpenWire
**Author:** Garland Glessner  
**License:** GNU General Public License v3.0  
**Contact:** gglesner@gmail.com  

## Overview
MinimalOpenWire is a Java-based command-line tool for interacting with Apache ActiveMQ brokers using the OpenWire protocol. It supports a variety of operations, including reading, writing, browsing, and monitoring queues and topics, as well as testing security vulnerabilities such as authentication bypass, authorization checks, and denial-of-service (DoS). The tool is designed for security researchers and administrators to test and audit ActiveMQ deployments.

## Features
- **Queue/Topic Operations:** Read, write, browse, or monitor queues and topics.
- **Advisory Monitoring:** Tracks ActiveMQ advisory messages for queue/topic changes.
- **Security Testing:**
  - Test authentication bypass with common credentials.
  - Check authorization on restricted destinations.
  - Detect information leakage (e.g., broker details, JMS metadata).
  - Perform DoS tests or send forged messages.
- **Destination Management:** List or delete queues and topics.
- **Cross-Platform:** Runs on macOS, Linux, and Windows with appropriate Java setup.

## Requirements
- **Java:** JDK 8 or later (e.g., Oracle JDK, OpenJDK).
- **Dependencies:** Apache ActiveMQ client libraries (included in `jars/` directory):
  - `activemq-client-<version>.jar`
  - Other required dependencies (e.g., `slf4j-api`, `jakarta.jms-api`).
- **Operating System:** macOS, Linux, or Windows.

## Installation

### Clone Repository
Obtain the `MinimalOpenWire.java` file and the `jars/` directory containing ActiveMQ client libraries.

### Install Java
1. Download and install a JDK (e.g., Oracle JDK or OpenJDK).
2. Ensure `java` and `javac` are in your system PATH.

### Verify Dependencies
Confirm that the `jars/` directory contains all required `.jar` files (e.g., `activemq-client`, `slf4j-api`).  
If missing, download from the [Apache ActiveMQ website](https://activemq.apache.org/) or [Maven Central](https://mvnrepository.com/).

## Compilation

### macOS/Linux
```bash
javac -cp .:$(echo ./jars/*.jar | tr ' ' ':') MinimalOpenWire.java
````

### Windows

```batch
@echo off
set CLASSPATH=.
for %%f in (.\jars\*.jar) do set CLASSPATH=!CLASSPATH!;%%f
javac -cp %CLASSPATH% MinimalOpenWire.java
```

## Usage

Run the tool with the specified operation and connection details:

### macOS/Linux

```bash
java -cp .:$(echo ./jars/*.jar | tr ' ' ':') MinimalOpenWire <method> <host> <port> <username> <password> <destinationName> [operation]
```

### Windows

```batch
@echo off
set CLASSPATH=.
for %%f in (.\jars\*.jar) do set CLASSPATH=!CLASSPATH!;%%f
java -cp %CLASSPATH% MinimalOpenWire %*
pause
```

### Arguments

* `<method>`: Connection transport (e.g., `tcp`, `ssl`).
* `<host>`: Broker host (e.g., `localhost`, `192.168.1.100`).
* `<port>`: Broker port (e.g., `61616` for OpenWire).
* `<username>`: Authentication username.
* `<password>`: Authentication password.
* `<destinationName>`: Queue or topic name (e.g., `TEST.QUEUE`).
* `[operation]`: Optional operation (defaults to `readwritequeue`):

  * `readqueue`, `readtopic`: Receive a message.
  * `writequeue`, `writetopic`: Send a test message.
  * `readwritequeue`, `readwritetopic`: Send and receive a message.
  * `browsequeue`: List messages in a queue.
  * `readqueueloop`, `readtopicloop`: Continuously receive messages.
  * `browsequeueloop`: Continuously browse a queue.
  * `monitoradvisory`: Monitor advisory topics.
  * `listall`: List all queues and topics.
  * `authenticationbypass`: Test common credentials.
  * `authorizationcheck`: Test access to restricted destinations.
  * `intercept`: Modify intercepted messages.
  * `dos`: Send 1000 messages for DoS testing.
  * `infoleak`: Retrieve broker and JMS metadata.
  * `forge`: Send a message with forged properties.
  * `deletequeue`, `deletetopic`: Delete a queue or topic.

## Examples

### Send a message to a queue:

```bash
java -cp .:./jars/* MinimalOpenWire tcp localhost 61616 admin admin TEST.QUEUE writequeue
```

### Monitor advisory topics:

```bash
java -cp .:./jars/* MinimalOpenWire tcp localhost 61616 admin admin Advisory monitoradvisory
```

### Test authentication bypass:

```bash
java -cp .:./jars/* MinimalOpenWire tcp localhost 61616 none none none authenticationbypass
```

## Directory Structure

```
MinimalOpenWire/
├── MinimalOpenWire.java           # Source code
├── jars/                          # ActiveMQ client libraries
│   ├── activemq-client-<version>.jar
│   ├── slf4j-api-<version>.jar
│   └── ...
├── LICENSE                        # GPL-3.0
└── README.md                      # This file
```

## Limitations

* **ActiveMQ-Specific:** Designed for ActiveMQ brokers using OpenWire; may not work with other JMS providers.
* **Security Testing:** Operations like `authenticationbypass`, `dos`, and `deletequeue` require authorization and may cause disruptions.
* **Dependencies:** Requires ActiveMQ client libraries in `jars/`.
* **No GUI:** Command-line only, unlike other HACKtiveMQ Suite tools.

## Troubleshooting

### Compilation Errors

* Ensure all `.jar` files are in `jars/`.
* Verify JDK is installed and `javac` is accessible.

### Connection Issues

* Check broker URL, host, and port (e.g., `tcp://localhost:61616`).
* Validate credentials or test with `authenticationbypass`.

### Operation Fails

* Confirm destination exists for queue/topic operations.
* Check logs for JMS exceptions (e.g., authorization errors).

### Missing Libraries

* Download missing `.jar` files from Maven Central.

## Disclaimer

This tool is for authorized security testing and administrative auditing only. Unauthorized use, including DoS attacks or unauthorized deletion of destinations, may violate laws or service terms. Use responsibly.

## Contributing

1. Fork the repository.
2. Create a feature branch: `git checkout -b feature/your-feature`.
3. Commit changes: `git commit -m "Add your feature"`.
4. Push: `git push origin feature/your-feature`.
5. Open a pull request.

Test changes with an ActiveMQ broker to ensure compatibility.

## License

Licensed under GNU GPL v3.0. See LICENSE for details.
