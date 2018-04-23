import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import net.imagej.ImageJ;
import org.slf4j.LoggerFactory;

public class MainClass {
    public static void main(String... args) {
        setLoggingLevel(Level.INFO);
        final ImageJ ij = new ImageJ();
        ij.launch(args);
        ij.command().run(PreProcessing.class, true);
    }

    public static void setLoggingLevel(Level level) {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(level);
    }
}
