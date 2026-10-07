import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class BabyDataStore {
    private static final Path DATA_FILE = Paths.get("baby-calendar-data.properties");
    private final Properties properties = new Properties();
    private Path file = DATA_FILE;

    public static BabyDataStore load() throws IOException {
        return load(DATA_FILE);
    }

    public static BabyDataStore load(Path file) throws IOException {
        BabyDataStore store = new BabyDataStore();
        store.file = file;
        if (Files.exists(file)) {
            try (InputStream input = Files.newInputStream(file)) {
                store.properties.load(input);
            }
        }
        return store;
    }

    public String get(String key) {
        return properties.getProperty(key);
    }

    public void set(String key, String value) {
        properties.setProperty(key, value);
    }

    public Properties properties() {
        return properties;
    }

    public void save() throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        try (OutputStream output = Files.newOutputStream(file)) {
            properties.store(output, "Baby Development Calendar - private local data");
        }
    }
}
