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

import com.google.gerrit.server.util.TimeUtil;
import com.googlesource.gerrit.plugins.rabbitmq.annotation.MessageHeader;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.Message;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.Section;
import com.rabbitmq.client.AMQP;

import org.apache.commons.codec.CharEncoding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class AMQProperties {

  public final static String EVENT_APPID = "gerrit";
  public final static String CONTENT_TYPE_JSON = "application/json";

  private static final Logger LOGGER = LoggerFactory.getLogger(AMQProperties.class);

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
            switch(f.getType().getSimpleName()) {
              case "String":
                headers.put(mh.value(), f.get(section).toString());
                break;
              case "Integer":
                headers.put(mh.value(), f.getInt(section));
                break;
              case "Long":
                headers.put(mh.value(), f.getLong(section));
                break;
              case "Boolean":
                headers.put(mh.value(), f.getBoolean(section));
                break;
              default:
                break;
            }
          } catch (Exception ex) {
            LOGGER.info(ex.getMessage());
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
