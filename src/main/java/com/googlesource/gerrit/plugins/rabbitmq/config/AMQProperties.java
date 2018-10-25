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

package com.googlesource.gerrit.plugins.rabbitmq.config;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.util.time.TimeUtil;
import com.googlesource.gerrit.plugins.rabbitmq.annotation.MessageHeader;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.Message;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.Section;
import com.rabbitmq.client.AMQP;
import java.lang.reflect.Field;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.codec.CharEncoding;

public class AMQProperties {

  public static final String EVENT_APPID = "gerrit";
  public static final String CONTENT_TYPE_JSON = "application/json";

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Message message;
  private final Map<String, Object> headers;

  public AMQProperties(PluginProperties properties) {
    this.message = properties.getSection(Message.class);
    this.headers = new HashMap<>();
    for (Section section : properties.getSections()) {
      for (Field f : section.getClass().getFields()) {
        if (f.isAnnotationPresent(MessageHeader.class)) {
          MessageHeader mh = f.getAnnotation(MessageHeader.class);
          try {
            Object value = f.get(section);
            if (value == null) {
              continue;
            }
            switch (f.getType().getSimpleName()) {
              case "String":
                headers.put(mh.value(), value.toString());
                break;
              case "Integer":
                headers.put(mh.value(), Integer.valueOf(value.toString()));
                break;
              case "Long":
                headers.put(mh.value(), Long.valueOf(value.toString()));
                break;
              case "Boolean":
                headers.put(mh.value(), Boolean.valueOf(value.toString()));
                break;
              default:
                break;
            }
          } catch (IllegalAccessException | IllegalArgumentException ex) {
            logger.atWarning().log(
                "Cannot access field %s. Cause: %s", f.getName(), ex.getMessage());
          }
        }
      }
    }
  }

  public AMQP.BasicProperties getBasicProperties() {
    return new AMQP.BasicProperties.Builder()
        .appId(EVENT_APPID)
        .contentEncoding(CharEncoding.UTF_8)
        .contentType(CONTENT_TYPE_JSON)
        .deliveryMode(message.deliveryMode)
        .priority(message.priority)
        .headers(headers)
        .timestamp(new Date(TimeUtil.nowMs()))
        .build();
  }
}
