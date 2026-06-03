// Canonical Sunmi inner-printer callback interface.
// Embedded so BetEse Vendor can bind to the built-in head without the vendor AAR.
package woyou.aidlservice.jiuiv5;

interface ICallback {
    void onRunResult(boolean isSuccess);
    void onReturnString(String result);
    void onRaiseException(int code, String msg);
    void onPrintResult(int code, String msg);
}
