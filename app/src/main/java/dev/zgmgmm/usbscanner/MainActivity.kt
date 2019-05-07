package dev.zgmgmm.usbscanner

import android.content.Context
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import dev.zgmgmm.esls.receiver.ZKCScanCodeBroadcastReceiver
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.debug
import org.jetbrains.anko.find
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue


class MainActivity : AppCompatActivity(), AnkoLogger {
    private lateinit var adapter: HistoryListAdapter
    private var autoSend: Boolean = false
    private lateinit var ssc: ServerSocketChannel
    private val channels = CopyOnWriteArrayList<SocketChannel>()
    private var port = 2333
    private var queue = LinkedBlockingQueue<String>()
    private lateinit var receiver: ZKCScanCodeBroadcastReceiver
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

        // view of info
        txtPort.text = port.toString()
        txtPort.setOnLongClickListener {
            // TODO
            // show input dialog
            true
        }


        // buttons
        swtAutoSend.setOnCheckedChangeListener { _, isChecked ->
            autoSend = isChecked
        }

        btnSend.setOnClickListener {
            queue.put(txtBarcode.text as String)
        }

        btnClear.setOnClickListener {
            adapter.clear()
        }

        Thread {
            listen(port)
        }.start()

        Thread {
            sendLoop()
        }.start()

        // register receiver
        receiver = ZKCScanCodeBroadcastReceiver.register(this, this::onResult)
    }

    override fun onDestroy() {
        // unregister receiver
        this.unregisterReceiver(receiver)
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.auto_option -> {
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun listen(port: Int) {
        ssc = ServerSocketChannel.open()
        ssc.socket().bind(InetSocketAddress(port))
        while (true) {
            val sc = ssc.accept()
            channels.add(sc)
        }
    }

    private fun onResult(barcode: String) {
        println(barcode)
        runOnUiThread {
            txtBarcode.text = barcode
            adapter.add(barcode)
        }
        if (autoSend) {
            queue.put(barcode)
        }
    }

    private fun sendLoop() {
        while (true) {
            val msg = queue.take() + "\n"
            val bb = ByteBuffer.wrap(msg.toByteArray())
            channels.forEach { channel ->
                bb.rewind()
                try {
                    channel.write(bb)
                } catch (e: Exception) {
                    channels.remove(channel)
                    debug(e)
                }
            }
        }
    }


}

class HistoryListAdapter(context: Context, resource: Int, objects: MutableList<String>) :
    ArrayAdapter<String>(context, resource, objects) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: View.inflate(context, R.layout.item_list_history, parent)
        val barcode = this.getItem(position)
        view.find<TextView>(R.id.text).text = barcode
        return view
    }

}
