package eu.hassanlab.rdnwdp;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import net.imagej.ImageJ;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Future;

public class MainClass {
    public static void main(String... args) {
        setLoggingLevel(Level.INFO);
        final ImageJ ij = new ImageJ();
        ij.launch(args);


        int received = 0;
        boolean errors = false;

        while(received < 1 && !errors) {
            Future future = ij.command().run(PreProcessing.class, true);
            try {
                Object result = future.get();
                received ++;
            }
            catch(Exception e) {
                errors = true;
            }
        }

        System.exit(0);
    }

    public static void setLoggingLevel(Level level) {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(level);
    }
}
