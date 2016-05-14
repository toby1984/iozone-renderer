This is just a quick hack to render IOZone data as 2d/3d charts.

![screenshot]()

### Building

```mvn clean package```

### Running

Since this is just a proof-of-concept, the code is picking up the input data from the classpath ; also since this is just a proof-of-concept, I didn't properly integrate the libgdx native library into the packaging process so you need to manually point the JVM to the library folder.

```java -Djava.library.path=lib -jar target/iozone-renderer.jar```
