# Apache CXF XJC Utils

A collection of XJC (JAXB XML-to-Java compiler) plugins and utilities for use with [Apache CXF](https://cxf.apache.org/). These extensions augment CXF's code generation pipeline when compiling XML Schemas and WSDL files into Java.

## Modules

| Module | Artifact | Description |
|---|---|---|
| `cxf-xjc-plugin` | `org.apache.cxf:cxf-xjc-plugin` | Maven plugin (goals `xsdtojava` and `xsdtojava-tests`) that runs XJC on standalone XML Schemas, optionally with the extensions below |
| `dv` | `org.apache.cxf.xjcplugins:cxf-xjc-dv` | `-Xdv` — initializes fields mapped from elements with the schema default values |
| `ts` | `org.apache.cxf.xjcplugins:cxf-xjc-ts` | `-Xts` — adds a `toString()` to generated classes that prints the value of every field (using `cxf-xjc-runtime` at runtime). This includes any sensitive data, such as passwords or tokens, so take care when logging generated objects |
| `boolean` | `org.apache.cxf.xjcplugins:cxf-xjc-boolean` | `-Xbg` — generates `getXXX()` instead of `isXXX()` for `Boolean` properties; `-Xbgi` generates both |
| `javadoc` | `org.apache.cxf.xjcplugins:cxf-xjc-javadoc` | `-Xjavadoc` — generates Javadoc from `xsd:documentation` |
| `property-listener` | `org.apache.cxf.xjcplugins:cxf-xjc-pl` | `-Xproperty-listener` — adds a `PropertyChangeListener` to all set methods |
| `wsdlextension` | `org.apache.cxf.xjcplugins:cxf-xjc-wsdlextension` | `-Xwsdlextension` — adds WSDL extension methods to generated root classes |
| `bug671` | `org.apache.cxf.xjcplugins:cxf-xjc-bug671` | `-Xbug671` — maps package names that contain Java keywords |
| `bug986` | `org.apache.cxf.xjcplugins:cxf-xjc-bug986` | `-Xbug986` — removes `@XmlSchemaType(anySimpleType)` from fields that should not have it |
| `runtime` | `org.apache.cxf.xjc-utils:cxf-xjc-runtime` | Runtime support classes used by code generated with some of the plugins (e.g. `-Xts`) |

Note that the artifacts use different groupIds: the Maven plugin is `org.apache.cxf:cxf-xjc-plugin`, while the XJC extensions are under `org.apache.cxf.xjcplugins`. When searching Maven Central, search by artifactId (e.g. `cxf-xjc-plugin`, `cxf-xjc-dv`) rather than by a single groupId.

## Versioning

CXF XJC Utils is released independently of Apache CXF, and its version numbers do **not** track CXF releases. For example, there is no XJC Utils release for every CXF patch release. Use the latest XJC Utils release from the same major line as your CXF version (4.x for CXF 4.x, which uses Jakarta XML Binding; 3.x for CXF 3.x, which uses `javax.xml.bind`). The available versions are listed on [Maven Central](https://central.sonatype.com/artifact/org.apache.cxf/cxf-xjc-plugin/versions).

## Usage

### With `cxf-codegen-plugin` (WSDL to Java)

Add the desired extension as a dependency of the `cxf-codegen-plugin` Maven plugin:

```xml
<plugin>
  <groupId>org.apache.cxf</groupId>
  <artifactId>cxf-codegen-plugin</artifactId>
  <dependencies>
    <dependency>
      <groupId>org.apache.cxf.xjcplugins</groupId>
      <artifactId>cxf-xjc-dv</artifactId>
      <version>${cxf-xjc-utils.version}</version>
    </dependency>
  </dependencies>
</plugin>
```

Then activate the plugin in your `wsdl2java` configuration:

```xml
<configuration>
  <defaultOptions>
    <extraargs>
      <extraarg>-xjc-Xdv</extraarg>
    </extraargs>
  </defaultOptions>
</configuration>
```

### With `cxf-xjc-plugin` (XML Schema to Java)

```xml
<plugin>
  <groupId>org.apache.cxf</groupId>
  <artifactId>cxf-xjc-plugin</artifactId>
  <version>${cxf-xjc-utils.version}</version>
  <executions>
    <execution>
      <id>xsdtojava</id>
      <goals>
        <goal>xsdtojava</goal>
      </goals>
      <configuration>
        <xsdOptions>
          <xsdOption>
            <extension>true</extension>
            <xsd>${basedir}/src/main/resources/schemas/foo.xsd</xsd>
            <extensionArgs>
              <arg>-Xdv</arg>
            </extensionArgs>
          </xsdOption>
        </xsdOptions>
        <extensions>
          <extension>org.apache.cxf.xjcplugins:cxf-xjc-dv:${cxf-xjc-utils.version}</extension>
        </extensions>
      </configuration>
    </execution>
  </executions>
</plugin>
```

## Requirements

- Java 11+
- Maven 3.x
- Apache CXF 4.x (see [Versioning](#versioning))

## Building from Source

```bash
git clone https://github.com/apache/cxf-xjc-utils.git
cd cxf-xjc-utils
mvn install
```

## License

Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
