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

package com.googlesource.gerrit.plugins.rabbitmq.worker;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventListener;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.rabbitmq.message.Publisher;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Singleton
public class DefaultEventWorker implements EventListener, EventWorker {

  private final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Set<Publisher> publishers = new CopyOnWriteArraySet<>();

  @Override
  public void addPublisher(Publisher publisher) {
    publishers.add(publisher);
  }

  @Override
  public void addPublisher(String pluginName, Publisher publisher, String userName) {
    logger.atWarning().log("addPublisher() with username '%s' was called. No-op.", userName);
  }

  @Override
  public void removePublisher(Publisher publisher) {
    publishers.remove(publisher);
  }

  @Override
  public void clear() {
    publishers.clear();
  }

  @Override
  public void onEvent(Event event) {
    for (Publisher publisher : publishers) {
      publisher.getEventListener().onEvent(event);
    }
  }
}
