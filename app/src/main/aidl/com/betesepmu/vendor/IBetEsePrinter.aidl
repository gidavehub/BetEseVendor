// Public AIDL for tight integration: a sister app (e.g. SWIPE) binds this service and
// prints through BetEse Vendor without touching ESC/POS or the head directly.
package com.betesepmu.vendor;

interface IBetEsePrinter {
    /** Enqueue plain text; returns the spooler job id. */
    long printText(String text);

    /** Enqueue a JsonReceipt document (see JsonReceipt schema); returns the job id. */
    long printJson(String json);

    /** Enqueue pre-encoded ESC/POS bytes; returns the job id. */
    long printRaw(in byte[] data);

    /** JSON describing transport + head status. */
    String status();
}
