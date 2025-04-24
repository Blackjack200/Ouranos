## Ouranos - Minecraft Bedrock Proxy

Ouranos is a proxy for Minecraft Bedrock Edition that enables players using any client version to connect seamlessly to
any server version by performing protocol-level translation without modifying the original server or client software.

## Core Features

- **Cross-Version Compatibility**: Transparently bridges different Bedrock protocol versions so older or newer clients
  can join servers regardless of version differences.
- **Low-Latency Forwarding**: Built upon the high-performance Netty framework and CloudburstMC/Protocol, Ouranos adds
  minimal overhead, ensuring that latency remains in the millisecond range even under heavy traffic.

## Installation & Usage

### 1. Download the Binary

1. Go to the [Releases](https://github.com/blackjack200/ouranos/releases) page and download `Ouranos.jar`.
2. Run:
   ```bash
   java -jar Ouranos.jar
   ```
3. A default `config.json` will be generated in the current directory on first run.

## Quick Start

1. **Start the Proxy**: Ensure `config.json` is set up, then launch Ouranos. The console will log the listening address
   and port.
2. **Client Connects**: In your Bedrock client, add a server pointing at the proxy IP and port (e.g.,
   `127.0.0.1:19133`).
3. **Version-Agnostic Play**: Clients of any supported version will join the backend server transparently.

## License

MIT License. See [LICENSE](LICENSE) for details.
