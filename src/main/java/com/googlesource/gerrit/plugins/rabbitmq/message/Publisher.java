package com.googlesource.gerrit.plugins.rabbitmq.message;

import com.google.gerrit.common.EventListener;
import com.googlesource.gerrit.plugins.rabbitmq.config.Properties;
import com.googlesource.gerrit.plugins.rabbitmq.session.Session;

public interface Publisher {
  void start();

  void stop();

  void enable();

  void disable();

  boolean isEnable();

  Session getSession();

  Properties getProperties();

  String getName();

  EventListener getEventListener();
}
