package speedNode.Utilities.Logs;

import java.io.File;
import java.io.IOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Logger;

public class MyLogger extends Logger {

    private String name;
    private String displayName;
    private String filepath;
    private boolean showMethods;

    /**
     * Protected method to construct a logger for a named subsystem.
     * <p>
     * The logger will be initially configured with a null Level
     * and with useParentHandlers set to true.
     *
     * @param name A name for the logger.  This should
     *             be a dot-separated name and should normally
     *             be based on the package name or class name
     *             of the subsystem, such as java.net
     *             or javax.swing.  It may be null for anonymous Loggers.
     * @param resourceBundleName name of ResourceBundle to be used for localizing
     *                           messages for this logger.  May be null if none
     *                           of the messages require localization.
     * @throws java.util.MissingResourceException if the resourceBundleName is non-null and
     *                                  no corresponding resource can be found.
     */
    protected MyLogger(String name, String resourceBundleName) {
        super(name, resourceBundleName);
    }

    /**
     *
     * @param name A name for the logger.  This should
     *             be a dot-separated name and should normally
     *             be based on the package name or class name
     *             of the subsystem, such as java.net
     *             or javax.swing.  It may be null for anonymous Loggers.
     * @param displayName Name shown in logging messages.
     * @param showMethods Should be true if the methods that created the log message should appear
     * @param path Path to the file where the logging messages will be stored. If null, only command logging will be performed.
     * @throws SecurityException When there is no permission to perform file logging
     * @throws IOException When an error occurs when trying to create/open a file to store the logging messages.
     */
    public static MyLogger createLogger(String name, String displayName, String path, boolean showMethods) {
        String resourceBundleName = Logger.getAnonymousLogger().getResourceBundleName();
        MyLogger myLogger = new MyLogger(name, resourceBundleName);

        myLogger.showMethods = showMethods;
        myLogger.displayName = displayName;
        myLogger.name = name;
        myLogger.filepath = path != null ? path + name + ".txt" : null;
        myLogger.setUseParentHandlers(false);

        MyFormatter formatter = new MyFormatter(displayName, showMethods);
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(formatter);
        myLogger.addHandler(consoleHandler);

        if(myLogger.filepath != null) {
            try {
                // This block configures the logger with handler and formatter
                FileHandler fh = new FileHandler(myLogger.filepath);
                fh.setFormatter(formatter);
                myLogger.addHandler(fh);
            } catch (SecurityException e) {
                throw new RuntimeException(new SecurityException("No permission to perform file logging."));
            } catch (IOException ioe) {
                throw new RuntimeException(new IOException("Error while trying to open the file '" + myLogger.filepath + "'."));
            }
        }

        return myLogger;
    }

    /**
     * Changes log name. If a filepath is associated, changes the name of the file.
     * @param newLogName new log name
     * @param displayName Name shown in logging messages.
     * @throws SecurityException When there is no permission to perform file logging
     * @throws IOException When an error occurs when trying to open/rename the new file to store the logging messages.
     */
    public void changeLogNameAndDisplayName(String newLogName, String displayName) {
        var handlers = this.getHandlers();

        //Removes previous console handler
        for(var handler : handlers){
            if(handler instanceof ConsoleHandler) {
                this.removeHandler(handler);
                handler.close();
                break;
            }
        }

        MyFormatter formatter = new MyFormatter(displayName, showMethods);
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(formatter);
        //Adds new console handler
        this.addHandler(consoleHandler);

        if(filepath != null) {
            String prevPath = filepath;
            String newPath = filepath.substring(0, filepath.length() - ".txt".length() - name.length()) + newLogName;

            // File (or directory) with old name
            File file = new File(prevPath);

            // File (or directory) with new name
            File file2 = new File(newPath);

            if (!file.renameTo(file2)) {
                this.warning("Could not change log file name from " + prevPath + " to " + newPath);
                return;
            }
            file.delete();

            filepath = newPath;

            FileHandler fh;
            try {
                // This block configures the logger with handler and formatter
                fh = new FileHandler(filepath);
                fh.setFormatter(formatter);
            } catch (SecurityException e) {
                throw new RuntimeException(new SecurityException("No permission to perform file logging."));
            } catch (IOException ioe) {
                throw new RuntimeException(new IOException("Error while trying to open the file '" + filepath + "'."));
            }

            //Removes previous file handler
            for(var handler : handlers){
                if(handler instanceof FileHandler) {
                    this.removeHandler(handler);
                    handler.close();
                    break;
                }
            }

            //Adds new file handler
            this.addHandler(fh);
        }

        name = newLogName;
    }
}
