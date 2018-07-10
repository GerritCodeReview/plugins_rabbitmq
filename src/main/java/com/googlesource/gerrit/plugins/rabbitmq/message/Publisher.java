package com.googlesource.gerrit.plugins.rabbitmq.message;

import com.google.gerrit.server.events.EventListener;
import com.googlesource.gerrit.plugins.rabbitmq.config.Properties;

public interface Publisher {
  void start();

  void stop();

  Properties getProperties();

  String getName();

  EventListener getEventListener();
}
