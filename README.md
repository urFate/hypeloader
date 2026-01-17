# HypeLoader
Java agent for Hytale server software which allows connections for offline mode clients.

## Usage
1. Download latest binary from [releases](https://github.com/urFate/hypeloader/releases) page
2. Put `hypeloader-agent-X.X-SNAPSHOT.jar` in the same directory with server jar
3. Add ```-javaagent:hypeloader-agent-X.X-SNAPSHOT.jar``` before `-jar` flag in the server execution command.

> [!IMPORTANT] 
> Don't forget to replace X.X with your **HypeLoader** version

### Building
1. Put `HytaleServer.jar` to project `libs` directory
2. Execute `jar` gradle task

## License

This software licenced under [Apache License Version 2.0](https://github.com/urFate/hypeloader/blob/main/LICENSE)
