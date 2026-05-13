# Apache CXF XJC Utils

A collection of XJC (JAXB XML-to-Java compiler) plugins and utilities for use with [Apache CXF](https://cxf.apache.org/). These extensions augment CXF's code generation pipeline when compiling XML Schemas and WSDL files into Java.

## Modules

| Module | Description |
|---|---|
| `bug671` | Fix for XJC bug 671 — corrects generated code for certain schema patterns |
| `bug986` | Fix for XJC bug 986 — corrects generated code for certain schema patterns |
| `dv` | Data validation plugin — annotates generated classes with Bean Validation (JSR-303) constraints derived from XML Schema facets (e.g. `minLength`, `maxOccurs`, `pattern`) |
| `ts` | Type substitution plugin — enables proper handling of `xsi:type` substitution in generated code |
| `wsdlextension` | WSDL extension utilities — supports additional WSDL processing during CXF code generation |

## Usage

The plugins are applied via the `cxf-codegen-plugin` Maven plugin. Add the desired artifact as a plugin dependency:

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

## Requirements

- Java 11+
- Maven 3.x
- Apache CXF 4.x (for the matching release line)

## Building from Source

```bash
git clone https://github.com/apache/cxf-xjc-utils.git
cd cxf-xjc-utils
mvn install
```

## License

Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
