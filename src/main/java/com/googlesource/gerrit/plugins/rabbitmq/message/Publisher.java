package com.googlesource.gerrit.plugins.rabbitmq.message;

import com.google.gerrit.common.EventListener;

import com.googlesource.gerrit.plugins.rabbitmq.config.Properties;
import com.googlesource.gerrit.plugins.rabbitmq.session.Session;

public interface Publisher {
  public void start();
  public void stop();
  public void enable();
  public void disable();
  public boolean isEnable();
  public Session getSession();
  public Properties getProperties();
  public String getName();
  public EventListener getEventListener();
}
