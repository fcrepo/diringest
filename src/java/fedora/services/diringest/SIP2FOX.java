package fedora.services.diringest;

import java.io.*;
import org.apache.log4j.*;
import org.apache.log4j.xml.*;

import fedora.services.diringest.pidgen.*;

/**
 * Command-line application for converting SIPs to FOXML files.
 */
public abstract class SIP2FOX {

    private static final Logger logger = 
            Logger.getLogger(SIP2FOX.class.getName());

    /**
     * Main entry point.
     */
    public static void main(String[] args) {
        String homeDir = System.getProperty("sip2fox.home");
        if (homeDir == null) {
            System.out.println("ERROR: sip2fox.home property not set.");
            System.exit(1);
        }
        File log4jConfig = new File(new File(homeDir), "config/log4j.xml");
        DOMConfigurator.configure(log4jConfig.getPath());
        if (args.length != 2) {
            System.err.println("ERROR: Two arguments required.");
            System.err.println();
            System.err.println("Usage: sip2fox sipFile.zip outputDir");
            System.exit(1);
        }
        try {
            long startTime = System.currentTimeMillis();
            logger.info("Initializing...");
            Converter c = new Converter(new TestPIDGenerator());
            logger.info("Processing...");
            FOXMLResult[] r = c.convert(new File(args[0]));
            logger.info("Saving results...");
            File dir = new File(args[1]);
            dir.mkdirs();
            for (int i = 0; i < r.length; i++) {
                File foxmlFile = new File(dir, r[i].getPID().toFilename());
                FileOutputStream outStream = new FileOutputStream(foxmlFile);
                r[i].dump(outStream);
                outStream.close();
                r[i].close();
                logger.info("Wrote " + foxmlFile.getPath());
            }
            long ms = System.currentTimeMillis() - startTime;
            System.out.println("SUCCESS: Converted SIP to " + r.length 
                    + " FOXML files in " + ms + "ms.");
        } catch (Throwable th) {
            if (logger.isDebugEnabled() || th.getMessage() == null) {
                th.printStackTrace();
            } else {
                System.err.println("ERROR: " + th.getMessage());
            }
            System.exit(1);
        }
    }
}