Build
=====

This plugin is built with Buck.

Two build modes are supported: Standalone and in Gerrit tree. Standalone
build mode is recommended, as this mode doesn't require local Gerrit
tree to exist.

Build standalone
----------------

Clone bucklets library:

```
  git clone https://gerrit.googlesource.com/bucklets

```
and link it to rabbitmq directory:

```
  cd rabbitmq && ln -s ../bucklets .
```

Add link to the .buckversion file:

```
  cd rabbitmq && ln -s bucklets/buckversion .buckversion
```

Add link to the .watchmanconfig file:

```
  cd rabbitmq && ln -s bucklets/watchmanconfig .watchmanconfig
```

To build the plugin, issue the following command:


```
  buck build plugin
```

The output is created in

```
  buck-out/gen/rabbitmq/rabbitmq.jar
```

Build in Gerrit tree
--------------------

Clone or link this plugin to the plugins directory of Gerrit's source
tree, and issue the command:

```
  buck build plugins/rabbitmq
```

The output is created in

```
  buck-out/gen/plugins/rabbitmq/rabbitmq.jar
```

This project can be imported into the Eclipse IDE:

```
  ./tools/eclipse/project.py
```

How to build the Gerrit Plugin API is described in the [Gerrit
documentation](../../../Documentation/dev-buck.html#_extension_and_plugin_api_jar_files).
