package com.betesepmu.vendor.integration.http

import android.util.Base64
import com.betesepmu.vendor.di.AppContainer
import com.betesepmu.vendor.document.JsonReceipt
import com.betesepmu.vendor.printer.PrintPayload
import com.betesepmu.vendor.spooler.JobSource
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject

/**
 * Local HTTP print surface. A browser-based or WebView POS on the same device (or LAN)
 * POSTs a job and BetEse Vendor owns the head. Off by default; toggled in Settings.
 *
 * Endpoints:
 *  - `GET  /`        → a self-contained HTML test console
 *  - `GET  /status`  → JSON head/transport status
 *  - `POST /print`   → enqueue a job. `text/plain` body → text; `application/json` body →
 *                      a [JsonReceipt] document, or `{ "escpos_base64": "…" }` for raw bytes.
 */
class PrintBrokerHttpServer(private val container: AppContainer) {

    private var server: Inner? = null

    val isRunning: Boolean get() = server?.isAlive == true
    val port: Int get() = container.settingsRepository.current.httpPort

    fun start(): Boolean {
        stop()
        return runCatching {
            Inner(port).also { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
                .also { server = it }
        }.isSuccess
    }

    fun stop() {
        runCatching { server?.stop() }
        server = null
    }

    private inner class Inner(port: Int) : NanoHTTPD(port) {
        override fun serve(session: IHTTPSession): Response = try {
            when {
                session.method == Method.GET && session.uri == "/" -> html(INDEX_HTML)
                session.method == Method.GET && session.uri == "/status" -> json(statusJson())
                session.method == Method.POST && session.uri == "/print" -> handlePrint(session)
                session.method == Method.OPTIONS -> cors(newFixedLengthResponse(""))
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
            }
        } catch (e: Exception) {
            json("""{"ok":false,"error":${JSONObject.quote(e.message ?: "error")}}""", Response.Status.INTERNAL_ERROR)
        }

        private fun handlePrint(session: IHTTPSession): Response {
            val body = HashMap<String, String>()
            session.parseBody(body)
            val raw = body["postData"] ?: ""
            val ctype = (session.headers["content-type"] ?: "").lowercase()

            val payload: PrintPayload = when {
                ctype.contains("json") -> jsonPayload(raw)
                else -> PrintPayload.PlainText(raw.ifBlank { session.queryParameterString ?: "" })
            }
            val id = container.enqueue("HTTP job", JobSource.HTTP, payload)
            return json("""{"ok":true,"jobId":$id}""")
        }

        private fun jsonPayload(raw: String): PrintPayload {
            val obj = JSONObject(raw)
            obj.optString("escpos_base64").takeIf { it.isNotEmpty() }?.let {
                return PrintPayload.Raw(Base64.decode(it, Base64.DEFAULT))
            }
            return PrintPayload.ReceiptDoc(JsonReceipt.parse(raw))
        }

        private fun statusJson(): String {
            val s = container.settingsRepository.current
            val t = container.transportManager.active.value
            val st = container.transportManager.status.value
            return JSONObject().apply {
                put("ok", true)
                put("transport", t.name)
                put("ready", st.ready)
                put("paperOut", st.paperOut)
                put("message", st.message)
                put("paperWidthMm", s.paperWidth.mm)
                put("port", port)
            }.toString()
        }

        private fun html(text: String) = cors(newFixedLengthResponse(Response.Status.OK, "text/html", text))
        private fun json(text: String, status: Response.Status = Response.Status.OK) =
            cors(newFixedLengthResponse(status, "application/json", text))

        private fun cors(r: Response): Response = r.apply {
            addHeader("Access-Control-Allow-Origin", "*")
            addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
            addHeader("Access-Control-Allow-Headers", "Content-Type")
        }
    }

    private companion object {
        val INDEX_HTML = """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>BetEse Vendor — Print API</title>
            <style>
              body{font-family:system-ui,Arial;margin:0;background:#f4f8f4;color:#143b22}
              header{background:#149e4b;color:#fff;padding:16px 20px;font-size:20px;font-weight:700}
              main{padding:20px;max-width:680px;margin:auto}
              textarea{width:100%;height:160px;font-family:monospace;border:1px solid #b9d8c4;border-radius:8px;padding:10px}
              button{background:#149e4b;color:#fff;border:0;border-radius:8px;padding:12px 18px;font-size:15px;cursor:pointer}
              code{background:#e7f3ea;padding:2px 5px;border-radius:4px}
              .card{background:#fff;border:1px solid #d8eadd;border-radius:12px;padding:16px;margin-bottom:16px}
              #out{white-space:pre-wrap;font-family:monospace}
            </style></head><body>
            <header>BetEse Vendor — Local Print API</header>
            <main>
              <div class="card">
                <p>POST a job to <code>/print</code>. JSON example below, or send <code>text/plain</code>.</p>
                <textarea id="payload">{ "elements": [
              { "type":"title", "text":"BetEse Vendor" },
              { "type":"center","text":"Local HTTP test" },
              { "type":"divider" },
              { "type":"row","left":"Item","right":"D 100.00" },
              { "type":"qr","data":"https://betese.example" },
              { "type":"cut" }
            ] }</textarea>
                <p><button onclick="send()">Send test print</button></p>
                <div id="out"></div>
              </div>
            </main>
            <script>
              async function send(){
                const out=document.getElementById('out'); out.textContent='Sending…';
                try{
                  const r=await fetch('/print',{method:'POST',headers:{'Content-Type':'application/json'},body:document.getElementById('payload').value});
                  out.textContent=await r.text();
                }catch(e){ out.textContent='Error: '+e; }
              }
            </script></body></html>
        """.trimIndent()
    }
}
