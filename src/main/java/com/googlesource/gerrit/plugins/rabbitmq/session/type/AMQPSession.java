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
import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownNotifier;
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

  private class ShutdownListenerImpl implements ShutdownListener {

    private final Class<?> clazz;

    <T extends ShutdownNotifier> ShutdownListenerImpl(Class<T> clazz) {
      this.clazz = clazz;
    }

    @Override
    public void shutdownCompleted(ShutdownSignalException cause) {
      if (cause != null) {
        Object obj = cause.getReference();
        if (Channel.class.isInstance(obj)) {
          Channel.class.cast(obj).removeShutdownListener(this);
        } else if (Connection.class.isInstance(obj)) {
          Connection.class.cast(obj).removeShutdownListener(this);
        }
        if (clazz.isInstance(obj)) {
          if (clazz == Channel.class) {
            Channel ch = Channel.class.cast(obj);
            if (cause.isInitiatedByApplication()) {
              logger.atInfo().log(MSG("Channel #%s closed by application."), ch.getChannelNumber());
            } else {
              logger.atWarning().log(
                  MSG("Channel #%sclosed. Cause: %s"), ch.getChannelNumber(), cause.getMessage());
            }
            if (ch.equals(AMQPSession.this.channel)) {
              AMQPSession.this.channel = null;
            }
          } else if (clazz == Connection.class) {
            Connection conn = Connection.class.cast(obj);
            if (cause.isInitiatedByApplication()) {
              logger.atInfo().log(MSG("Connection closed by application."));
            } else {
              logger.atWarning().log(MSG("Connection closed. Cause: %s"), cause.getMessage());
            }
            if (conn.equals(AMQPSession.this.connection)) {
              AMQPSession.this.connection = null;
            }
          }
        }
      }
    }
  }

  private final Properties properties;
  private volatile Connection connection;
  private volatile Channel channel;

  private final AtomicInteger failureCount = new AtomicInteger(0);
  private final ShutdownListener connectionListener = new ShutdownListenerImpl(Connection.class);
  private final ShutdownListener channelListener = new ShutdownListenerImpl(Channel.class);

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
    Channel ch = null;
    if (connection == null) {
      connect();
    } else {
      try {
        ch = connection.createChannel();
        ch.addShutdownListener(channelListener);
        failureCount.set(0);
        logger.atInfo().log(MSG("Channel #%s opened."), ch.getChannelNumber());
      } catch (IOException | AlreadyClosedException ex) {
        logger.atSevere().withCause(ex).log(MSG("Failed to open channel."));
        failureCount.incrementAndGet();
      }
      if (failureCount.get() > properties.getSection(Monitor.class).failureCount) {
        logger.atWarning().log("Connection has something wrong. So will be disconnected.");
        disconnect();
      }
    }
    return ch;
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
        connection.addShutdownListener(connectionListener);
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
        logger.atInfo().log(MSG("Closing Channel #%s..."), channel.getChannelNumber());
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
    } catch (IOException ex) {
      logger.atSevere().withCause(ex).log(MSG("Error when closing connection."));
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
