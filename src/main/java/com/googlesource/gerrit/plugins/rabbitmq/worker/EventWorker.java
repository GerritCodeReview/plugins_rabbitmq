package com.googlesource.gerrit.plugins.rabbitmq.worker;

import com.googlesource.gerrit.plugins.rabbitmq.message.Publisher;

public interface EventWorker {
  void addPublisher(Publisher publisher);
  void addPublisher(Publisher publisher, String userName);
  void removePublisher(Publisher publisher);
  void clear();
}
