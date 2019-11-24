package helpers;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;


/**
 * Logger class that creates CSV file to then plot agents behavior.
 */
public class Logger {

    private String fileName;
    private PrintWriter writer;

    public Logger(String fileName) {
        this.fileName = fileName;

        try {
            this.writer = new PrintWriter(new File(fileName));

            StringBuilder sb = new StringBuilder();
            sb.append("Task number");
            sb.append(',');
            sb.append("Current reward");
            sb.append('\n');

            writer.write(sb.toString());

        } catch (FileNotFoundException e) {
            System.out.println(e.getMessage());
        }
    }

    public void logToFile(int taskNumber, double currentReward) {

        StringBuilder sb = new StringBuilder();
        sb.append(taskNumber);
        sb.append(',');
        sb.append(currentReward);
        sb.append('\n');

        writer.write(sb.toString());
        writer.flush();
    }
}
