package com.shyamanand.singlethreadedscheduler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Scanner;


public class App {

    private static final Logger logger = LogManager.getLogger(App.class);

    public static void main(String[] args) {
        SingleThreadedScheduler scheduler = new SingleThreadedScheduler();
        scheduler.start();

        Scanner scanner = new Scanner(System.in);
        while (scheduler.isRunning()) {
            System.out.print("> ");
            String userInput = scanner.nextLine();

            if (userInput.equalsIgnoreCase("show")) {
                System.out.println(scheduler.getQueueContents());
                continue;
            }

            if (userInput.equalsIgnoreCase("status")) {
                System.out.println(scheduler.getSchedulerState());
            }

            if (userInput.equalsIgnoreCase("quit")) {
                scheduler.shutdown();
                break;
            }

            String[] cmd = userInput.split(" ");
            if (cmd.length == 2) {
                scheduler.scheduleWithDelay(
                        () -> logger.info(cmd[0]),
                        cmd[0],
                        Long.parseLong(cmd[1]) * 1000);
            } else {
                System.out.println("Unknown command");
            }
        }
        scanner.close();
    }

}

