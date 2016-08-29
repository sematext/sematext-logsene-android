package sematext.com.testapp;

import android.app.Application;

import com.sematext.android.Logsene;

/**
 * Custom application class to define the uncaught exception handler.
 */
public class TestApplication extends Application {
    private Thread.UncaughtExceptionHandler defaultExceptionHandler;
    private Thread.UncaughtExceptionHandler exceptionHandler = new Thread.UncaughtExceptionHandler() {
        @Override
        public void uncaughtException(Thread thread, Throwable ex) {
            // Send uncaught exception to Logsene.
            Logsene logsene = new Logsene(TestApplication.this);
            logsene.error(ex);

            // Run the default android handler if one is set
            if (defaultExceptionHandler != null) {
                defaultExceptionHandler.uncaughtException(thread, ex);
            }
        }
    };

    public TestApplication() {
        defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(exceptionHandler);
    }
}
