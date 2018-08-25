package TimerHelper;

import java.util.TimerTask;

class SSCTimerTask extends TimerTask {

    private final TimeoutListener _timeoutListener;
    public SSCTimerTask(TimeoutListener timeoutListener)
    {
        _timeoutListener = timeoutListener;
    }
    @Override
    public void run() {
        try {
            _timeoutListener.onTimeoutExceeded();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
