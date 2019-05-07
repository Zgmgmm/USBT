package dev.zgmgmm.usbscanner

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.text.InputType
import android.view.*
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.google.zxing.Result
import dev.zgmgmm.esls.receiver.ZKCScanCodeBroadcastReceiver
import kotlinx.android.synthetic.main.activity_main.*
import me.dm7.barcodescanner.zxing.ZXingScannerView
import org.jetbrains.anko.*
import java.io.Closeable
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue


class MainActivity : AppCompatActivity(),AnkoLogger {

    private lateinit var adapter: HistoryListAdapter
    private var autoSend: Boolean = true
    private lateinit var ssc: ServerSocketChannel
    private val channels = CopyOnWriteArrayList<SocketChannel>()
    private var port = 2333
    private var queue = LinkedBlockingQueue<String>()
    private lateinit var receiver: ZKCScanCodeBroadcastReceiver
    private lateinit var listenThread: Thread
    private lateinit var sendThread: Thread
    private var toast: Toast? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // list of history
        val data = LinkedList<String>()
        adapter = HistoryListAdapter(this, R.layout.item_list_history, data)
        listHistory.adapter = adapter
        listHistory.setOnItemClickListener { _, _, position, _ ->
            txtBarcode.text = adapter.getItem(position)
        }


        // buttons
        swtAutoSend.isChecked = autoSend
        swtAutoSend.setOnCheckedChangeListener { _, isChecked ->
            autoSend = isChecked
        }

        btnSend.setOnClickListener {
            send(txtBarcode.text as String)
        }

        btnClear.setOnClickListener {
            clearHistory()
        }

        // start server
        listen(port)
        sendLoop()

        // register receiver
        receiver = ZKCScanCodeBroadcastReceiver.register(this, this::onResult)
    }

    override fun onDestroy() {
        // unregister receiver
        this.unregisterReceiver(receiver)
        stopServer()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.main, menu)
        return true
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.reboot -> {
                val builder = AlertDialog.Builder(this)
                builder.setTitle( getString(R.string.on_reboot))
                builder.setPositiveButton(getString(R.string.dialog_ok)) { _, _ -> reboot() }
                builder.setNegativeButton(getString(R.string.dialog_cancel)) { dialog, _ -> dialog.cancel() }
                builder.show()
            }
            R.id.config -> {
                val builder = AlertDialog.Builder(this)
                builder.setTitle(getString(R.string.set_port))
                val input = EditText(this)
                input.inputType = InputType.TYPE_CLASS_NUMBER
                input.setText(port.toString())
                builder.setView(input)
                builder.setPositiveButton(getString(R.string.dialog_ok)) { _, _ -> setPort(input.text.toString().toInt()); }
                builder.setNegativeButton(getString(R.string.dialog_cancel)) { dialog, _ -> dialog.cancel() }
                builder.show()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setPort(port: Int) {
        if(port==this.port){
            return
        }
        this.port = port
        txtPort.text = port.toString()
        defaultSharedPreferences.edit().putInt("port",port).apply()
        stopServer()
        listen(port)
    }

    private fun stopServer() {
        ssc.close()
        channels.forEach { closeQuietly(it) }
        channels.clear()
        updateClients(0)
    }


    /**
     * toast without waiting for previous toast
     */
    private fun imToast(message: CharSequence){
        if(toast!=null){
            toast!!.cancel()
        }
        toast=toast(message)

    }
    private fun send(barcode: String) {
        if (barcode.isEmpty()) {
            imToast(getString(R.string.no_barcode_to_send))
            return
        }
        if (channels.isEmpty()) {
            imToast(getString(R.string.no_clients_to_send))
            return
        }
        imToast(getString(R.string.barcode_sent))
        adapter.add(barcode)
        listHistory.smoothScrollToPosition(adapter.count-1)
        queue.put(txtBarcode.text as String)
    }

    private fun onResult(barcode: String) {
        info(barcode)
        runOnUiThread {
            txtBarcode.text = barcode
        }
        if (autoSend) {
            send(barcode)
        }
    }

    private fun closeQuietly(o: Closeable) = try {
        o.close()
    } catch (e: Exception) {
    }

    private fun clearHistory() {
        txtBarcode.text = null
        adapter.clear()
        updateSent(0)
    }

    private fun updateClients(clients: Int) = runOnUiThread { txtClients.text = clients.toString() }
    private fun updateSent(sent: Int) = runOnUiThread { txtSent.text = sent.toString() }


    private fun listen(port: Int) {
        updateClients(0)
        listenThread = Thread {
            try {
                ssc = ServerSocketChannel.open()
                ssc.socket().bind(InetSocketAddress(port))
                while (true) {
                    val sc = ssc.accept()
                    channels.add(sc)
                    updateClients(channels.count())
                }
            } catch (e: Exception) {
                info(e)
            } finally {
                closeQuietly(ssc)
            }
        }
        listenThread.start()
    }


    private fun sendLoop() {
        sendThread = Thread {
            channels.clear()
            while (true) {
                val barcode: String = queue.take()
                val msg = barcode + "\n"
                val bb = ByteBuffer.wrap(msg.toByteArray())
                channels.forEach { channel ->
                    bb.rewind()
                    try {
                        channel.write(bb)
                    } catch (e: Exception) {
                        closeQuietly(channel)
                        channels.remove(channel)
                        debug(e)
                    }
                }

                // update stat
                runOnUiThread {
                    txtSent.text = adapter.count.toString()
                    txtClients.text = channels.count().toString()
                }
            }

        }
        sendThread.start()
    }

    private fun reboot() {
        stopServer()
        val intent = packageManager.getLaunchIntentForPackage(packageName)!!;
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }
}

class HistoryListAdapter(context: Context, resource: Int, objects: MutableList<String>) :
    ArrayAdapter<String>(context, resource, objects) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_list_history, null)
        val barcode = this.getItem(position)
        view.find<TextView>(R.id.text).text = barcode
        return view
    }

}
