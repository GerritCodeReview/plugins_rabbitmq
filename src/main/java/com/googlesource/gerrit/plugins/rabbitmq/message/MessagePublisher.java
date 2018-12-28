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
  private final LinkedBlockingQueue<Event> queue = new LinkedBlockingQueue<>(MAX_EVENTS);
  private final Object sessionMon = new Object();
  private EventListener eventListener;
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
          private int lostEventCount = 0;

          @Override
          public void onEvent(Event event) {
            if (!publisherThread.isAlive()) {
              ensurePublisherThreadStarted();
            }

            if (queue.offer(event)) {
              if (lostEventCount > 0) {
                LOGGER.warn("Event queue is no longer full, {} events were lost", lostEventCount);
                lostEventCount = 0;
              }
            } else {
              if (lostEventCount++ % 10 == 0) {
                LOGGER.error("Event queue is full, lost {} event(s)", lostEventCount);
              }
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
                Event event = queue.take();
                while (!isConnected() && !canceled) {
                  synchronized (sessionMon) {
                    sessionMon.wait(1000);
                  }
                }
                if (!publishEvent(event) && !queue.offer(event)) {
                  LOGGER.error("Event lost: {}", gson.toJson(event));
                }
              } catch (InterruptedException e) {
                LOGGER.warn("Interupted while waiting for event or connection.", e);
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
    ensurePublisherThreadStarted();
    if (!isConnected()) {
      connect();
      monitorTimer.schedule(
          new TimerTask() {
            @Override
            public void run() {
              if (!isConnected()) {
                LOGGER.info("#start: try to reconnect");
                connect();
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
        publisherThread.join(2000);
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

  private boolean publishEvent(Event event) {
    return session.publish(gson.toJson(event));
  }

  private void connect() {
    if (!isConnected() && session.connect()) {
      synchronized (sessionMon) {
        sessionMon.notifyAll();
      }
    }
  }

  private synchronized void ensurePublisherThreadStarted() {
    if (publisherThread == null || !publisherThread.isAlive()) {
      LOGGER.info("Creating new publisher thread.");
      publisherThread = new Thread(publisher);
      publisherThread.setName("rabbitmq-publisher");
      publisherThread.start();
    }
  }
}
