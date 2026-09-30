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

    public static BabyDataStore load() throws IOException {
        BabyDataStore store = new BabyDataStore();
        if (Files.exists(DATA_FILE)) {
            try (InputStream input = Files.newInputStream(DATA_FILE)) {
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
        try (OutputStream output = Files.newOutputStream(DATA_FILE)) {
            properties.store(output, "Baby Development Calendar - private local data");
        }
    }
}
