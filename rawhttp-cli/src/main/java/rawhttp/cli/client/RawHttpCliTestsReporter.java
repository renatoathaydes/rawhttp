package rawhttp.cli.client;

import com.athaydes.rawhttp.reqinedit.HttpTestResult;
import com.athaydes.rawhttp.reqinedit.HttpTestsReporter;

public class RawHttpCliTestsReporter implements HttpTestsReporter {
    @Override
    public void report(HttpTestResult result) {
        long time = result.getEndTime() - result.getStartTime();
        if (result.isSuccess()) {
            System.out.println("TEST OK (" + time + "ms): " + result.getName());
        } else {
            System.out.println("TEST FAILED (" + time + "ms): " + result.getName());
            if (!"".equals(result.getError())) {
                System.err.println(result.getError());
            }
        }
    }
}
