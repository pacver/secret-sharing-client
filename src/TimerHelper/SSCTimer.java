package TimerHelper;

import java.util.Timer;

public class SSCTimer {

    private final long timeOut;
    private final Timer timer;
    private final SSCTimerTask timerTask;

    public SSCTimer(long timeOutInSec, TimeoutListener timeoutListener)
    {
        timeOut = timeOutInSec*1000;
        timer = new Timer();
        timerTask = new SSCTimerTask(timeoutListener);
    }

    public void startTimer()
    {
        timer.schedule(timerTask,timeOut);
    }

    public void startTimerTaskImmediately(){
        timer.cancel();
        timerTask.run();
    }

    public void stopTimer()
    {
        if(timer.purge()!=0)
            timer.cancel();
    }
}
