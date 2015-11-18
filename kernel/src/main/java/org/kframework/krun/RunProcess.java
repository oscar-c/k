// Copyright (c) 2014-2015 K Team. All Rights Reserved.
package org.kframework.krun;

import org.kframework.utils.errorsystem.KEMException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Map;
import java.util.function.Supplier;

// instantiate processes
public class RunProcess {

    public static Thread getOutputStreamThread(Process p2, Supplier<InputStream> in, PrintStream out) {
        return new Thread(() -> {
                    int count;
                    byte[] buffer = new byte[8192];
                    try {
                        while (true) {
                            count = in.get().read(buffer);
                            if (count < 0)
                                break;
                            out.write(buffer, 0, count);
                        }
                    } catch (IOException e) {}
                });
    }

    public static class ProcessOutput {
        public final byte[] stdout;
        public final byte[] stderr;
        public final int exitCode;

        public ProcessOutput(byte[] stdout, byte[] stderr, int exitCode) {
            this.stdout = stdout;
            this.stderr = stderr;
            this.exitCode = exitCode;
        }
    }

    private RunProcess() {}

    public static ProcessOutput execute(Map<String, String> environment, ProcessBuilder pb, String... commands) {


        try {
            if (commands.length <= 0) {
                throw KEMException.criticalError("Need command options to run");
            }

            // create process
            pb = pb.command(commands);
            Map<String, String> realEnvironment = pb.environment();
            realEnvironment.putAll(environment);

            // start process
            Process process = pb.start();

            ByteArrayOutputStream out, err;
            PrintStream outWriter, errWriter;
            out = new ByteArrayOutputStream();
            err = new ByteArrayOutputStream();
            outWriter = new PrintStream(out);
            errWriter = new PrintStream(err);

            Thread outThread = getOutputStreamThread(process, process::getInputStream, outWriter);
            Thread errThread = getOutputStreamThread(process, process::getErrorStream, errWriter);

            outThread.start();
            errThread.start();

            // wait for process to finish
            process.waitFor();

            outThread.join();
            errThread.join();
            outWriter.flush();
            errWriter.flush();

            return new ProcessOutput(out.toByteArray(), err.toByteArray(), process.exitValue());

        } catch (IOException | InterruptedException e) {
            throw KEMException.criticalError("Error while running process:" + e.getMessage(), e);
        }

    }

}
