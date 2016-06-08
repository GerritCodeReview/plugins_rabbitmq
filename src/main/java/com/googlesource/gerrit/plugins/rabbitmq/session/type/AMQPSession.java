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

import com.googlesource.gerrit.plugins.rabbitmq.config.Properties;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.AMQP;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.Exchange;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.Gerrit;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.Message;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.Monitor;
import com.googlesource.gerrit.plugins.rabbitmq.session.Session;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownNotifier;
import com.rabbitmq.client.ShutdownSignalException;

import org.apache.commons.codec.CharEncoding;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public final class AMQPSession implements Session {

  private class ShutdownListenerImpl implements ShutdownListener {

    private final Class<?> clazz;

    public <T extends ShutdownNotifier> ShutdownListenerImpl(Class<T> clazz) {
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
              LOGGER.info(MSG("Channel #{} closed by application."), ch.getChannelNumber());
            } else {
              LOGGER.warn(MSG("Channel #{} closed. Cause: {}"),
                ch.getChannelNumber(), cause.getMessage());
            }
            if (ch.equals(AMQPSession.this.channel)) {
              AMQPSession.this.channel = null;
            }
          } else if (clazz == Connection.class) {
            Connection conn = Connection.class.cast(obj);
            if (cause.isInitiatedByApplication()) {
              LOGGER.info(MSG("Connection closed by application."));
            } else {
              LOGGER.warn(MSG("Connection closed. Cause: {}"), cause.getMessage());
            }
            if (conn.equals(AMQPSession.this.connection)) {
              AMQPSession.this.connection = null;
            }
          }
        }
      }
    }
  }

  private static final Logger LOGGER = LoggerFactory.getLogger(AMQPSession.class);
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
        LOGGER.info(MSG("Channel #{} opened."), ch.getChannelNumber());
      } catch (IOException ex) {
        LOGGER.error(MSG("Failed to open channel."), ex);
        failureCount.incrementAndGet();
      }
      if (failureCount.get() > properties.getSection(Monitor.class).failureCount) {
        LOGGER.warn("Connection has something wrong. So will be disconnected.");
        disconnect();
      }
    }
    return ch;
  }

  @Override
  public void connect() {
    if (connection != null && connection.isOpen()) {
      LOGGER.info(MSG("Already connected."));
      return;
    }
    AMQP amqp = properties.getSection(AMQP.class);
    LOGGER.info(MSG("Connect to {}..."), amqp.uri);
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
        LOGGER.info(MSG("Connection established."));
      }
    } catch (URISyntaxException ex) {
      LOGGER.error(MSG("URI syntax error: {}"), amqp.uri);
    } catch (IOException ex) {
      LOGGER.error(MSG("Connection cannot be opened."), ex);
    } catch (KeyManagementException | NoSuchAlgorithmException ex) {
      LOGGER.error(MSG("Security error when opening connection."), ex);
    }
  }

  @Override
  public void disconnect() {
    LOGGER.info(MSG("Disconnecting..."));
    try {
      if (channel != null) {
        LOGGER.info(MSG("Closing Channel #{}..."), channel.getChannelNumber());
        channel.close();
      }
    } catch (IOException | TimeoutException ex) {
      LOGGER.error(MSG("Error when closing channel."), ex);
    } finally {
      channel = null;
    }

    try {
      if (connection != null) {
        LOGGER.info(MSG("Closing Connection..."));
        connection.close();
      }
    } catch (IOException ex) {
      LOGGER.error(MSG("Error when closing connection."), ex);
    } finally {
      connection = null;
    }
  }

  @Override
  public void publish(String messageBody) {
    if (channel == null || !channel.isOpen()) {
      channel = getChannel();
    }
    if (channel != null && channel.isOpen()) {
      Message message = properties.getSection(Message.class);
      Exchange exchange = properties.getSection(Exchange.class);
      try {
        LOGGER.debug(MSG("Sending message."));
        channel.basicPublish(exchange.name, message.routingKey,
            properties.getAMQProperties().getBasicProperties(),
            messageBody.getBytes(CharEncoding.UTF_8));
      } catch (IOException ex) {
        LOGGER.error(MSG("Error when sending meessage."), ex);
      }
    }
  }
}
