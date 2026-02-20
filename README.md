# Cipher Decryptor IntelliJ Plugin

An IntelliJ IDEA plugin that decrypts `{cipher}...` configuration values using a Java KeyStore (JKS).

## Features

- **Settings Page**: Configure your Java KeyStore settings under `Settings → Tools → Cipher Decryptor`
- **Inlay Hints**: See decrypted values as inline hints in properties and YAML files. Left-click to decrypt cipher values and copy to clipboard

## Configuration

Configure the following settings in `Settings → Tools → Cipher Decryptor`:

| Setting | Description | Example |
|---------|-------------|---------|
| **KeyStore Location** | Path to the .jks file | `/path/to/keystore.jks` or `classpath:/keystore.jks` |
| **KeyStore Alias** | The key alias in the keystore | `mykey` |
| **KeyStore Password** | Password to open the keystore | `changeit` |
| **KeyStore Secret** | Password to access the key (optional, uses KeyStore Password if empty) | `secretpassword` |

These correspond to Spring Cloud Config encryption properties:
- `-Dencrypt.key-store.location`
- `-Dencrypt.key-store.alias`
- `-Dencrypt.key-store.password`
- `-Dencrypt.key-store.secret`

## Usage

1. Configure your KeyStore settings in the plugin settings
2. Open a `.properties` or `.yaml` file containing `{cipher}...` values
3. The plugin will show decrypted values as inline hints (masked for security)
4. Hover over the hint to see the full decrypted value
5. Left-click → "Decrypt Cipher Value" to copy the decrypted value to clipboard

## Example

```properties
# Your config file
spring.datasource.password={cipher}AQBHYmJLOGh2L2VOc...

# Will show inline hint:
# spring.datasource.password={cipher}AQBHYmJLOGh2L2VOc... → sec***rd
```

## Building

```bash
./gradlew build
```

## Running/Debugging

```bash
./gradlew runIde
```

## Installing

1. Build the plugin: `./gradlew buildPlugin`
2. The plugin ZIP will be in `build/distributions/`
3. In IntelliJ IDEA: `Settings → Plugins → ⚙️ → Install Plugin from Disk...`

## Security Notes

- KeyStore passwords are stored securely using IntelliJ's Password Safe
- Decrypted values in hints are masked by default (showing only first 3 and last 2 characters)
- Full decrypted values are only shown on hover or when explicitly copied

## Requirements

- IntelliJ IDEA 2023.3 or later
- Java 17 or later

## License

MIT License
