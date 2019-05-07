package dev.zgmgmm.usbscanner

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.google.zxing.Result
import me.dm7.barcodescanner.zxing.ZXingScannerView
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info

class SimpleScannerActivity : AppCompatActivity(), ZXingScannerView.ResultHandler, AnkoLogger {
    override fun handleResult(rawResult: Result) {
        // Do something with the result here
        info(rawResult.text) // Prints scan results
        info(rawResult.barcodeFormat.toString()) // Prints the scan format (qrcode, pdf417 etc.)
        // If you would like to resume scanning, call this method below:
        scannerView.resumeCameraPreview(this)

    }

    private lateinit var scannerView: ZXingScannerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scannerView = ZXingScannerView(this)   // Programmatically initialize the scanner view
        setContentView(scannerView)                // Set the scanner view as the content view
    }

    override fun onResume() {
        super.onResume()
        scannerView.setResultHandler(this) // Register ourselves as a handler for scan results.
        scannerView.startCamera()          // Start camera on resume
    }

    override fun onPause() {
        super.onPause()
        scannerView.stopCamera()           // Stop camera on pause
    }

}