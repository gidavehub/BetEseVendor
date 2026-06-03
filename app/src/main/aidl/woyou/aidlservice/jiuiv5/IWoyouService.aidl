// Canonical Sunmi inner-printer service interface (woyou.aidlservice.jiuiv5).
//
// This is the publicly documented IWoyouService contract, embedded so the broker can
// bind to the built-in print head on Sunmi terminals without pulling the vendor's AAR.
// AIDL transaction codes are positional, so the method ORDER here must match the version
// of the service running on the device. BetEse Vendor drives the head almost entirely
// through sendRAWData(byte[]) + printBitmap(Bitmap), which keeps it portable; for a
// firmware-specific build, drop in Sunmi's official SDK and point SunmiTransport at it.
package woyou.aidlservice.jiuiv5;

import woyou.aidlservice.jiuiv5.ICallback;
import android.graphics.Bitmap;

interface IWoyouService {

    void printerInit(in ICallback callback);

    void printerSelfChecking(in ICallback callback);

    String getPrinterSerialNo();

    String getPrinterVersion();

    String getPrinterModal();

    void getPrintedLength(in ICallback callback);

    int updatePrinterState();

    void sendRAWData(in byte[] data, in ICallback callback);

    void setAlignment(int alignment, in ICallback callback);

    void setFontName(String typeface, in ICallback callback);

    void setFontSize(float fontsize, in ICallback callback);

    void printText(String text, in ICallback callback);

    void printTextWithFont(String text, String typeface, float fontsize, in ICallback callback);

    void printOriginalText(String text, in ICallback callback);

    void printColumnsText(in String[] colsTextArr, in int[] colsWidthArr, in int[] colsAlign, in ICallback callback);

    void printColumnsString(in String[] colsTextArr, in int[] colsWidthArr, in int[] colsAlign, in ICallback callback);

    void printBitmap(in Bitmap bitmap, in ICallback callback);

    void printBarCode(String data, int symbology, int height, int width, int textposition, in ICallback callback);

    void printQRCode(String data, int modulesize, int errorlevel, in ICallback callback);

    void print2DCode(String data, int symbology, int modulesize, int errorlevel, in ICallback callback);

    void commitPrinterBuffer();

    void enterPrinterBuffer(boolean clean);

    void exitPrinterBuffer(boolean commit);

    void lineWrap(int n, in ICallback callback);

    void cutPaper(in ICallback callback);

    int getCutPaperTimes();

    void openDrawer(in ICallback callback);

    int getOpenDrawerTimes();

    void printBitmapCustom(in Bitmap bitmap, int type, in ICallback callback);
}
