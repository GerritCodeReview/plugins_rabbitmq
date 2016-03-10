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

import com.google.gerrit.common.UserScopedEventListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PluginUser;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;

import com.googlesource.gerrit.plugins.rabbitmq.message.Publisher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class UserEventWorker implements EventWorker {

  private static final Logger LOGGER = LoggerFactory.getLogger(UserEventWorker.class);

  private final DynamicSet<UserScopedEventListener> eventListeners;
  private final WorkQueue workQueue;
  private final AccountResolver accountResolver;
  private final IdentifiedUser.GenericFactory userFactory;
  private final ThreadLocalRequestContext threadLocalRequestContext;
  private final PluginUser pluginUser;
  private final SchemaFactory<ReviewDb> schemaFactory;
  private final Map<Publisher, RegistrationHandle> eventListenerRegistrations;

  @Inject
  public UserEventWorker(
      DynamicSet<UserScopedEventListener> eventListeners,
      WorkQueue workQueue,
      AccountResolver accountResolver,
      IdentifiedUser.GenericFactory userFactory,
      ThreadLocalRequestContext threadLocalRequestContext,
      PluginUser pluginUser,
      SchemaFactory<ReviewDb> schemaFactory) {
    this.eventListeners = eventListeners;
    this.workQueue = workQueue;
    this.accountResolver = accountResolver;
    this.userFactory = userFactory;
    this.threadLocalRequestContext = threadLocalRequestContext;
    this.pluginUser = pluginUser;
    this.schemaFactory = schemaFactory;
    eventListenerRegistrations = new HashMap<>();
  }

  @Override
  public void addPublisher(final Publisher publisher) {
    LOGGER.warn("addPublisher() without username was called. Hence no operation.");
  }

  @Override
  public void addPublisher(final Publisher publisher, final String userName) {
    workQueue.getDefaultQueue().submit(new Runnable() {
      private ReviewDb db;
      private Account userAccount;

      @Override
      public void run() {
        RequestContext old = threadLocalRequestContext
            .setContext(new RequestContext() {

              @Override
              public CurrentUser getUser() {
                return pluginUser;
              }

              @Override
              public Provider<ReviewDb> getReviewDbProvider() {
                return new Provider<ReviewDb>() {
                  @Override
                  public ReviewDb get() {
                    if (db == null) {
                      try {
                        db = schemaFactory.open();
                      } catch (OrmException e) {
                        throw new ProvisionException("Cannot open ReviewDb", e);
                      }
                    }
                    return db;
                  }
                };
              }
            });
        try {
          userAccount = accountResolver.find(userName);
          if (userAccount == null) {
            LOGGER.error("No single user could be found when searching for listenAs: {}", userName);
            return;
          }
          final IdentifiedUser user = userFactory.create(userAccount.getId());
          RegistrationHandle registration =
              eventListeners.add(new UserScopedEventListener() {
                @Override
                public void onEvent(Event event) {
                  publisher.onEvent(event);
                }
                @Override
                public CurrentUser getUser() {
                  return user;
                }
              });
          eventListenerRegistrations.put(publisher, registration);
          LOGGER.info("Listen events as : {}", userName);
        } catch (OrmException e) {
          LOGGER.error("Could not query database for listenAs", e);
          return;
        } finally {
          threadLocalRequestContext.setContext(old);
          if (db != null) {
            db.close();
            db = null;
          }
        }
      }
    });
  }

  @Override
  public void removePublisher(final Publisher publisher) {
    RegistrationHandle eventListenerRegistration =
        eventListenerRegistrations.remove(publisher);
    if (eventListenerRegistration != null) {
      eventListenerRegistration.remove();
    }
  }

  @Override
  public void clear() {
    // no op.
  }
}
