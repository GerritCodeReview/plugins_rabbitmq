// Copyright (C) 2015 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.rabbitmq.message;

import com.google.gerrit.common.EventListener;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.git.WorkQueue.CancelableRunnable;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.googlesource.gerrit.plugins.rabbitmq.config.Properties;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.AMQP;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.Gerrit;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.Monitor;
import com.googlesource.gerrit.plugins.rabbitmq.session.Session;
import com.googlesource.gerrit.plugins.rabbitmq.session.SessionFactoryProvider;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessagePublisher implements Publisher, LifecycleListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(MessagePublisher.class);

  private static final int MONITOR_FIRSTTIME_DELAY = 15000;

  private static final int MAX_EVENTS = 16384;
  private final Session session;
  private final Properties properties;
  private final Gson gson;
  private final Timer monitorTimer = new Timer();
  private EventListener eventListener;
  private final LinkedBlockingQueue<Event> queue = new LinkedBlockingQueue<>(MAX_EVENTS);
  private CancelableRunnable publisher;
  private Thread publisherThread;

  @Inject
  public MessagePublisher(
      @Assisted final Properties properties,
      SessionFactoryProvider sessionFactoryProvider,
      Gson gson) {
    this.session = sessionFactoryProvider.get().create(properties);
    this.properties = properties;
    this.gson = gson;
    this.eventListener =
        new EventListener() {
          @Override
          public void onEvent(Event event) {
            try {
              if (!publisherThread.isAlive()) {
                publisherThread.start();
              }
              queue.put(event);
            } catch (InterruptedException e) {
              LOGGER.warn("Failed to queue event", e);
            }
          }
        };
    this.publisher =
        new CancelableRunnable() {

          boolean canceled = false;

          @Override
          public void run() {
            while (!canceled) {
              try {
                if (isConnected()) {
                  Event event = queue.poll(200, TimeUnit.MILLISECONDS);
                  if (event != null) {
                    if (isConnected()) {
                      publishEvent(event);
                    } else {
                      queue.put(event);
                    }
                  }
                } else {
                  Thread.sleep(1000);
                }
              } catch (InterruptedException e) {
                LOGGER.warn("Interupted while taking event", e);
              }
            }
          }

          @Override
          public void cancel() {
            this.canceled = true;
          }

          @Override
          public String toString() {
            return "Rabbitmq publisher: "
                + properties.getSection(Gerrit.class).listenAs
                + "-"
                + properties.getSection(AMQP.class).uri;
          }
        };
  }

  @Override
  public void start() {
    publisherThread = new Thread(publisher);
    publisherThread.start();
    if (!isConnected()) {
      session.connect();
      monitorTimer.schedule(
          new TimerTask() {
            @Override
            public void run() {
              if (!isConnected()) {
                LOGGER.info("#start: try to reconnect");
                session.connect();
              }
            }
          },
          MONITOR_FIRSTTIME_DELAY,
          properties.getSection(Monitor.class).interval);
    }
  }

  @Override
  public void stop() {
    monitorTimer.cancel();
    publisher.cancel();
    if (publisherThread != null) {
      try {
        publisherThread.join();
      } catch (InterruptedException e) {
        // Do nothing
      }
    }
    session.disconnect();
  }

  @Override
  public Properties getProperties() {
    return properties;
  }

  @Override
  public String getName() {
    return properties.getName();
  }

  @Override
  public EventListener getEventListener() {
    return this.eventListener;
  }

  private boolean isConnected() {
    return session != null && session.isOpen();
  }

  private void publishEvent(Event event) {
    session.publish(gson.toJson(event));
  }
}
