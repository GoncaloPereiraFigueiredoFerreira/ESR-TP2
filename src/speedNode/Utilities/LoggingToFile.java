package speedNode.Utilities;

import java.io.File;
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class LoggingToFile {
    /**
     * Creates logger that writes to a given file
     * @param logName Name of the log file. Mandatory
     * @param pathToDirectory Path to the directory where the log file will be stored. If null, the file is created in the directory where the program is running
     * @return logger that writes to a given file
     */
    public static Logger createLogger (String logName, String pathToDirectory, boolean logToStdOut){
        pathToDirectory = pathToDirectory == null ? "" : pathToDirectory;
        Logger logger = Logger.getLogger(logName);
        logger.setUseParentHandlers(logToStdOut);
        FileHandler fh;

        try {
            // This block configures the logger with handler and formatter
            fh = new FileHandler(pathToDirectory + logName);
            logger.addHandler(fh);
            SimpleFormatter formatter = new SimpleFormatter();
            fh.setFormatter(formatter);
        } catch (SecurityException e) {
            System.out.println("No permission to perform logging.");
        } catch (IOException ioe) {
            System.out.println("There was a problem opening the file '" + pathToDirectory + logName + "'.");
        }

        return logger;
    }

    public static void changeLogFile(Logger logger, String prevLogName, String prevPathToDir,
                                     String newLogName, String newPathToDir) {

        // File (or directory) with old name
        File file = new File(prevPathToDir + prevLogName);

        // File (or directory) with new name
        File file2 = new File(newLogName + newPathToDir);

        if (!file.renameTo(file2)) {
            logger.warning("Could not change log file name from " + prevPathToDir + prevLogName + " to " + newLogName + newPathToDir);
            return;
        }

        file.delete();

        FileHandler fh;
        try {
            //Removes previous file handler
            Handler handlerToRemove = logger.getHandlers()[0];
            logger.removeHandler(handlerToRemove);
            handlerToRemove.close();

            // This block configures the logger with the new handler and formatter
            fh = new FileHandler(newLogName + newPathToDir);
            logger.addHandler(fh);
            SimpleFormatter formatter = new SimpleFormatter();
            fh.setFormatter(formatter);
        } catch (SecurityException e) {
            System.out.println("No permission to perform logging.");
        } catch (IOException ioe) {
            System.out.println("There was a problem opening the file '" + newLogName + newPathToDir + "'.");
        }
    }
}
