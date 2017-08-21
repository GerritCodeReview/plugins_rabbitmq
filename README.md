gerrit-rabbitmq-plugin: Gerrit event publish plugin via RabbitMQ
=======================

Synopsis
----------------------

Publish gerrit stream events to a RabbitMQ queue.
This plugin works with any version of Gerrit starting from v2.8.

Environments
---------------------

* `linux`
* `java-1.8`
* `Bazel`

Reference
---------------------

* [Build]
* [Configuration]
* [Message Format]

[Build]: src/main/resources/Documentation/build.md
[Configuration]: src/main/resources/Documentation/config.md
[Message Format]: src/main/resources/Documentation/message.md

Minimum Configuration
---------------------

```
  [amqp]
    uri = amqp://localhost
  [exchange]
    name = exchange-for-gerrit-queue
  [message]
    routingKey = com.foobar.www.gerrit
  [gerrit]
    name = foobar-gerrit
    hostname = www.foobar.com
```

History
---------------------

* 3.14 (Freezed)
  * New branch: `stable-2.10`
  * HEAD in `master` is permanently latest but would not be updated by author anymore.

* 3.1
  * Fix README

* 3.0
  * New feature: multi url support
  * Bump amqp-client to 3.5.0
  * Bump Gradle shadow plugin to 1.2.1
  * Add Gradle release plugin 2.0.2
  * Fix & improve connection handling

* 2.0
  * The feature that configure queue/exchange/bind has been removed.
    Means messages are published to existing exhange only.
  * Allow event filter based on an existing user (Thanks @GLundh!)
  * Fix singletonize Properties class and fix typo (Thanks @hugares!)
  * Add API support: 2.9-2.10
  * Bumped default Gerrit API to 2.10
  * Bumped amqp-client to 3.4.4
  * Bumped Gradle to 2.3
  * Remove Buck support

* 1.4
  * Binary release
  * Add gradle support
  * Remove maven support

* 1.3
  * Build with Buck
  * Bumped api version to 2.8.3

* 1.2
  * Fix repository location for gerrit-api
  * Update README

* 1.1
  * Fix channel handling
  * Add property: `monitor.failureCount`
  * Update README and documents

* 1.0
  *  First release

License
---------------------

The Apache Software License, Version 2.0

Copyright
---------------------

Copyright (c) 2013 rinrinne a.k.a. rin_ne
