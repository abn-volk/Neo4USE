# Neo4USE

## What is it?

**Neo4USE** is a [USE](https://sourceforge.net/projects/useocl/) plug-in for importing/exporting UML models to/from Neo4j databases. At the moment, it is still in its infancy. The importing/exporting features have been implemented, but the USE-Neo4j mapping is not stable and is expected to change.

## Compilation/Development

To develop this plug-in, you'll need:

 * **JDK** version 8 or later
 * **[Apache Maven](https://maven.apache.org/index.html)** version 3
 * A Java IDE (**Eclipse** is recommended)
 * **Neo4j** version 3.2.3 or newer 
 * **[USE](https://sourceforge.net/projects/useocl/)** version 4.2.0 or higher

### Import to Eclipse

1. First, clone this project. Download USE from the link above and extract it.
2. Import *USE*: Choose *File > New > Project... > Java Project from Existing Ant Bundle* and point to the `build.xml` file of *USE*. 
3. Import this project as a Maven project: *File > Import... > Existing Maven Projects*. Ignore the missing JAR dependencies for now, or delete them in `pom.xml` if you don't need to compile the plug-in with Maven (see option 2 below).
4. Add *USE* to the Java build path: Go to *Project > Properties > Java Build Path*. In the *Project* tab, choose *Add...* and select the previously imported *USE* project. This is not strictly needed when compiling with Maven, but it allows viewing *USE*'s source when developing.
5. You can build this plug-in in two ways:

### Compile with Eclipse

This is faster and easier to set up. However, since the Maven dependencies are not included in the final JAR, you need to download them and copy them to the plug-in's class path. It is recommended for rapid development.

First, remove the dependencies with `<groupId>uet</groupId>` from `pom.xml`. They are needed in the latter approach but not required in the former.

Second, from the project directory, open a command prompt and execute `mvn dependency:copy-dependencies` to download *Neo4j* and related JARs. They will be available under `target/dependencies`. Copy these JARs into `lib/plugins/neo4use` under *USE*'s directory.

Third, add the following lines to `META-INF/MANIFEST.MF` if they're not there (pay attention to the leading spaces). They correspond to the previously downloaded JAR files. If there are changes in the dependencies, correct the lines to reflect them (or regenerate the lines with a script, e.g. `for file in *.jar; do echo " neo4use/${file}"; done`).
```
Class-Path: neo4use/asm-5.2.jar 
 neo4use/bcpkix-jdk15on-1.53.jar 
 neo4use/bcprov-jdk15on-1.53.jar 
 neo4use/caffeine-2.3.3.jar 
 neo4use/commons-compress-1.12.jar 
 neo4use/commons-lang3-3.3.2.jar 
 neo4use/concurrentlinkedhashmap-lru-1.4.2.jar 
 neo4use/lucene-analyzers-common-5.5.0.jar 
 neo4use/lucene-backward-codecs-5.5.0.jar 
 neo4use/lucene-codecs-5.5.0.jar 
 neo4use/lucene-core-5.5.0.jar 
 neo4use/lucene-queryparser-5.5.0.jar 
 neo4use/neo4j-3.2.3.jar 
 neo4use/neo4j-codegen-3.2.3.jar 
 neo4use/neo4j-collections-3.2.3.jar 
 neo4use/neo4j-command-line-3.2.3.jar 
 neo4use/neo4j-common-3.2.3.jar 
 neo4use/neo4j-configuration-3.2.3.jar 
 neo4use/neo4j-consistency-check-3.2.3.jar 
 neo4use/neo4j-csv-3.2.3.jar 
 neo4use/neo4j-cypher-3.2.3.jar 
 neo4use/neo4j-cypher-compiler-2.3-2.3.11.jar 
 neo4use/neo4j-cypher-compiler-3.1-3.1.5.jar 
 neo4use/neo4j-cypher-compiler-3.2-3.2.3.jar 
 neo4use/neo4j-cypher-frontend-2.3-2.3.11.jar 
 neo4use/neo4j-cypher-frontend-3.1-3.1.5.jar 
 neo4use/neo4j-cypher-frontend-3.2-3.2.3.jar 
 neo4use/neo4j-cypher-ir-3.2-3.2.3.jar 
 neo4use/neo4j-dbms-3.2.3.jar 
 neo4use/neo4j-graph-algo-3.2.3.jar 
 neo4use/neo4j-graph-matching-3.1.3.jar 
 neo4use/neo4j-graphdb-api-3.2.3.jar 
 neo4use/neo4j-import-tool-3.2.3.jar 
 neo4use/neo4j-index-3.2.3.jar 
 neo4use/neo4j-io-3.2.3.jar 
 neo4use/neo4j-jmx-3.2.3.jar 
 neo4use/neo4j-kernel-3.2.3.jar 
 neo4use/neo4j-logging-3.2.3.jar 
 neo4use/neo4j-lucene-index-3.2.3.jar 
 neo4use/neo4j-lucene-upgrade-3.2.3.jar 
 neo4use/neo4j-primitive-collections-3.2.3.jar 
 neo4use/neo4j-resource-3.2.3.jar 
 neo4use/neo4j-ssl-3.2.3.jar 
 neo4use/neo4j-udc-3.2.3.jar 
 neo4use/neo4j-unsafe-3.2.3.jar 
 neo4use/netty-all-4.1.8.Final.jar 
 neo4use/opencsv-2.3.jar 
 neo4use/parboiled-core-1.1.7.jar 
 neo4use/parboiled-scala_2.11-1.1.7.jar 
 neo4use/scala-library-2.11.8.jar 
 neo4use/scala-reflect-2.11.8.jar
```

To build the plug-in, select *File > Export... > JAR File*. Choose *USE*'s `lib/plugins` directory as the destination and choose *Next* twice. In *JAR Manifest Specification*, browse to `META-INF/MANIFEST.MF`. Click *Finish* and start *USE* - the plug-in's buttons should appear in the main toolbar.

### Compile with Maven
This approach utilises Maven's [Shade plugin](https://maven.apache.org/plugins/maven-shade-plugin/) to include all required class files in the final JAR. Therefore, the resulting file is very large, but it does not depend on *Neo4j* and its dependencies' JAR files.

First, copy the JAR files under *USE*'s `lib/` library (except for *JUnit*) to another directory. Install them to your local Maven repo. For example, in *bash*:
```
for file in *.jar
do
    echo "mvn install:install-file -Dfile=$file -DgroupId=uet -DartifactId=$file -Dversion=0.0.1 -Dpackaging=jar -DgeneratePom=true"
done
```

If necessary, regenerate Maven dependencies with the following commands:
```
for file in *.jar
do
    echo "<dependency>"
    echo "  <groupId>uet</groupId>"
    echo "  <artifactId>${file}</artifactId>"
    echo "  <version>0.0.1</version>"
    echo "</dependency>"
done
```
and put the output under `pom.xml`'s `<dependency>`.

To build the plug-in, type `mvn clean package` under the project's directory. Copy the JAR file from `targets/` to *USE*'s `lib/plugins`.
