### JDG 7.1 Compat Mode sample

This project demonstrate interoperability between Rest, Hot Rod and Memcached endpoints
for JDG 7.1.

#### Preparing the server

1. Download JDG 7.1 and change the file ```standalone/configuration/clustered.xml```

* Add a cache called ```compat```. This cache will be accessed via all protocols

```xml
<distributed-cache name="compat">
      <compatibility enabled="true"/>
</distributed-cache>
```

2. Configured the memcached endpoint to use the cache:

Add ```cache="compat"``` to the memcached-connector:

```xml
<memcached-connector socket-binding="memcached" 
                                        cache-container="clustered" cache="compat"/>

```

3. Create a user to be able to access the server via rest.

In the server ```bin/``` folder, run in order to create the user and password as ```user```:

```bash
bin/add-user.sh -u user -p user -a
```

#### Run the test

Start the server with ```bin/standalone.sh -c clustered.xml```

Execute the main method of class ```org.infinispan.TestCompat``` in your IDE, or via maven do:

```mvn install exec:java -s settings.xml```






