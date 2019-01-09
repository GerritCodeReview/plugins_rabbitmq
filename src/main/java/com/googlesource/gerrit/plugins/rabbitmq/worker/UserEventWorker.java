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
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PluginUser;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.UserScopedEventListener;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.rabbitmq.message.Publisher;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jgit.errors.ConfigInvalidException;

public class UserEventWorker implements EventWorker {

  private final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final DynamicSet<UserScopedEventListener> eventListeners;
  private final WorkQueue workQueue;
  private final AccountResolver accountResolver;
  private final IdentifiedUser.GenericFactory userFactory;
  private final ThreadLocalRequestContext threadLocalRequestContext;
  private final PluginUser pluginUser;
  private final Map<Publisher, RegistrationHandle> eventListenerRegistrations;

  @Inject
  public UserEventWorker(
      DynamicSet<UserScopedEventListener> eventListeners,
      WorkQueue workQueue,
      AccountResolver accountResolver,
      IdentifiedUser.GenericFactory userFactory,
      ThreadLocalRequestContext threadLocalRequestContext,
      PluginUser pluginUser) {
    this.eventListeners = eventListeners;
    this.workQueue = workQueue;
    this.accountResolver = accountResolver;
    this.userFactory = userFactory;
    this.threadLocalRequestContext = threadLocalRequestContext;
    this.pluginUser = pluginUser;
    eventListenerRegistrations = new HashMap<>();
  }

  @Override
  public void addPublisher(final Publisher publisher) {
    logger.atWarning().log("addPublisher() without username was called. No-op.");
  }

  @Override
  public void addPublisher(
      final String pluginName, final Publisher publisher, final String userName) {
    workQueue
        .getDefaultQueue()
        .submit(
            new Runnable() {
              private Account userAccount;

              @Override
              public void run() {
                RequestContext old =
                    threadLocalRequestContext.setContext(
                        new RequestContext() {

                          @Override
                          public CurrentUser getUser() {
                            return pluginUser;
                          }
                        });
                try {
                  userAccount = accountResolver.find(userName);
                  if (userAccount == null) {
                    logger.atSevere().log("Cannot find account for listenAs: %s", userName);
                    return;
                  }
                  final IdentifiedUser user = userFactory.create(userAccount.getId());
                  RegistrationHandle registration =
                      eventListeners.add(
                          pluginName,
                          new UserScopedEventListener() {
                            @Override
                            public void onEvent(Event event) {
                              publisher.getEventListener().onEvent(event);
                            }

                            @Override
                            public CurrentUser getUser() {
                              return user;
                            }
                          });
                  eventListenerRegistrations.put(publisher, registration);
                  logger.atInfo().log("Listen events as : %s", userName);
                } catch (OrmException | ConfigInvalidException | IOException e) {
                  logger.atSevere().withCause(e).log("Could not query database for listenAs");
                  return;
                } finally {
                  threadLocalRequestContext.setContext(old);
                }
              }
            });
  }

  @Override
  public void removePublisher(final Publisher publisher) {
    RegistrationHandle registration = eventListenerRegistrations.remove(publisher);
    if (registration != null) {
      registration.remove();
    }
  }

  @Override
  public void clear() {
    // no op.
  }
}
