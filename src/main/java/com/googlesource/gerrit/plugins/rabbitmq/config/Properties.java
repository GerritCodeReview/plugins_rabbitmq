package com.googlesource.gerrit.plugins.rabbitmq.config;

import com.googlesource.gerrit.plugins.rabbitmq.config.section.Section;
import java.nio.file.Path;
import java.util.Set;
import org.eclipse.jgit.lib.Config;

public interface Properties extends Cloneable {
  Config toConfig();

  boolean load();

  boolean load(Properties baseProperties);

  Path getPath();

  String getName();

  Set<Section> getSections();

  <T extends Section> T getSection(Class<T> clazz);

  AMQProperties getAMQProperties();
}
