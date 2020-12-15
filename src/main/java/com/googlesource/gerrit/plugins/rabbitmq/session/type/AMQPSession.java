// Copyright (C) 2013 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.plugins.rabbitmq.session.type;

import com.google.common.flogger.FluentLogger;
import com.googlesource.gerrit.plugins.rabbitmq.config.Properties;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.AMQP;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.Exchange;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.Gerrit;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.Message;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.Monitor;
import com.googlesource.gerrit.plugins.rabbitmq.session.Session;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ShutdownSignalException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.codec.CharEncoding;
import org.apache.commons.lang.StringUtils;

public final class AMQPSession implements Session {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Properties properties;
  private volatile Connection connection;
  private volatile Channel channel;

  private final AtomicInteger failureCount = new AtomicInteger(0);

  public AMQPSession(Properties properties) {
    this.properties = properties;
  }

  private String MSG(String msg) {
    return String.format("[%s] %s", properties.getName(), msg);
  }

  @Override
  public boolean isOpen() {
    if (connection != null) {
      return true;
    }
    return false;
  }

  private Channel getChannel() {
    if (connection == null) {
      connect();
    } else {
      try {
        final Channel ch = connection.createChannel();
        ch.addShutdownListener(
            cause -> {
              if (cause.isInitiatedByApplication()) {
                logger.atInfo().log(
                    MSG("Channel #%d closed by application."), ch.getChannelNumber());
              } else {
                logger.atWarning().log(
                    MSG("Channel #%dclosed. Cause: %s"), ch.getChannelNumber(), cause.getMessage());
              }
              if (ch.equals(AMQPSession.this.channel)) {
                AMQPSession.this.channel = null;
              }
            });
        failureCount.set(0);
        logger.atInfo().log(MSG("Channel #%d opened."), ch.getChannelNumber());
        return ch;
      } catch (IOException | AlreadyClosedException ex) {
        logger.atSevere().withCause(ex).log(MSG("Failed to open channel."));
        failureCount.incrementAndGet();
      }
      if (failureCount.get() > properties.getSection(Monitor.class).failureCount) {
        logger.atWarning().log(
            "Creating channel failed %d times, closing connection.", failureCount.get());
        disconnect();
      }
    }
    return null;
  }

  @Override
  public boolean connect() {
    if (connection != null && connection.isOpen()) {
      logger.atInfo().log(MSG("Already connected."));
      return true;
    }
    AMQP amqp = properties.getSection(AMQP.class);
    logger.atInfo().log(MSG("Connect to %s..."), amqp.uri);
    ConnectionFactory factory = new ConnectionFactory();
    try {
      if (StringUtils.isNotEmpty(amqp.uri)) {
        factory.setUri(amqp.uri);
        if (StringUtils.isNotEmpty(amqp.username)) {
          factory.setUsername(amqp.username);
        }
        Gerrit gerrit = properties.getSection(Gerrit.class);
        String securePassword = gerrit.getAMQPUserPassword(amqp.username);
        if (StringUtils.isNotEmpty(securePassword)) {
          factory.setPassword(securePassword);
        } else if (StringUtils.isNotEmpty(amqp.password)) {
          factory.setPassword(amqp.password);
        }
        connection = factory.newConnection();
        connection.addShutdownListener(
            cause -> {
              if (cause.isInitiatedByApplication()) {
                logger.atInfo().log(MSG("Connection closed by application."));
              } else {
                logger.atWarning().log(MSG("Connection closed. Cause: %s"), cause.getMessage());
              }
              if (connection.equals(AMQPSession.this.connection)) {
                AMQPSession.this.connection = null;
              }
            });
        logger.atInfo().log(MSG("Connection established."));
        return true;
      }
    } catch (URISyntaxException ex) {
      logger.atSevere().log(MSG("URI syntax error: %s"), amqp.uri);
    } catch (IOException | TimeoutException ex) {
      logger.atSevere().withCause(ex).log(MSG("Connection cannot be opened."));
    } catch (KeyManagementException | NoSuchAlgorithmException ex) {
      logger.atSevere().withCause(ex).log(MSG("Security error when opening connection."));
    }
    return false;
  }

  @Override
  public void disconnect() {
    logger.atInfo().log(MSG("Disconnecting..."));
    try {
      if (channel != null) {
        logger.atInfo().log(MSG("Closing Channel #%d..."), channel.getChannelNumber());
        channel.close();
      }
    } catch (IOException | TimeoutException ex) {
      logger.atSevere().withCause(ex).log(MSG("Error when closing channel."));
    } finally {
      channel = null;
    }

    try {
      if (connection != null) {
        logger.atInfo().log(MSG("Closing Connection..."));
        connection.close();
      }
    } catch (IOException | ShutdownSignalException ex) {
      logger.atWarning().withCause(ex).log(MSG("Error when closing connection."));
    } finally {
      connection = null;
    }
  }

  @Override
  public boolean publish(String messageBody) {
    if (channel == null || !channel.isOpen()) {
      channel = getChannel();
    }
    if (channel != null && channel.isOpen()) {
      Message message = properties.getSection(Message.class);
      Exchange exchange = properties.getSection(Exchange.class);
      try {
        logger.atFine().log(MSG("Sending message."));
        channel.basicPublish(
            exchange.name,
            message.routingKey,
            properties.getAMQProperties().getBasicProperties(),
            messageBody.getBytes(CharEncoding.UTF_8));
        return true;
      } catch (IOException ex) {
        logger.atSevere().withCause(ex).log(MSG("Error when sending meessage."));
        return false;
      }
    }
    logger.atSevere().log(MSG("Cannot open channel."));
    return false;
  }
}
