package limax.android.demo

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.content.DialogInterface;
import kotlinx.android.synthetic.main.activity_main.*
import com.otaliastudios.cameraview.Frame
import com.otaliastudios.cameraview.Gesture
import com.otaliastudios.cameraview.GestureAction
//import com.otaliastudios.cameraview.Grid

class MainActivity : AppCompatActivity() {
    @Volatile private var decoding = false
    @Volatile private var dialogOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        qrInitJNI()

        //camera.grid = Grid.DRAW_3X3
        camera.mapGesture(Gesture.TAP, GestureAction.FOCUS_WITH_MARKER)
        camera.mapGesture(Gesture.PINCH, GestureAction.ZOOM)

        camera.addFrameProcessor {
            if (!decoding && !dialogOn) {
                decoding = true
                var frame:Frame = it
                var data = frame.data
                var size = frame.size
                var format = frame.format
                if (format == android.graphics.ImageFormat.NV21) {

                    var result = qrDecodeJNI(data, size.width, size.height)
                    if (result != null) {
                        dialogOn = true
                        runOnUiThread {
                            initAlert(result.toString())
                        }
                    }
                }
                decoding = false
            }
        }
        camera.captureSnapshot()
    }

    private fun initAlert(msg:String) {
        AlertDialog.Builder(this)
                .setMessage(msg)
                .setTitle("QRScan Result")
                .setPositiveButton("OK", DialogInterface.OnClickListener { _, _ ->
                        dialogOn = false
                    })
                .setCancelable(false)
                .create()
                .show()
    }

    override fun onResume() {
        super.onResume()
        camera.start()
    }

    override fun onPause() {
        super.onPause()
        camera.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        camera.destroy()
    }

    external fun qrInitJNI()

    external fun qrDecodeJNI(data:ByteArray , width:Int, height:Int): QRInfo?

    companion object {
        init {
            System.loadLibrary("native-lib")
        }
    }
}
