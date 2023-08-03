package com.xt.demo

import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import com.xt.test.view.BarrageSurfaceView
import com.xt.test.view.BarrageView

class BarrageActivity : AppCompatActivity() {
    val TAG = this.javaClass.simpleName

    private var testHandler: Handler? = null

    private var barrageView: BarrageView? = null
    private var damuView: BarrageSurfaceView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_barrage)
        barrageView = findViewById(R.id.barrageView)
        damuView = findViewById(R.id.damuView)

        testHandler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                super.handleMessage(msg)
                if (msg.what == 100) {
                    var isTrue = Math.random() > 0.5f
                    var isColor = Math.random() > 0.5f
                    damuView?.addItemView(if (isTrue) "${System.currentTimeMillis()}" else "123412") {
                        if (isColor) {
                            it.background = Color.parseColor("#4d000000")
                        } else {
                            it.background = Color.parseColor("#80d92e20")
                        }
                    }
                    barrageView?.addItemView(if (isTrue) "${System.currentTimeMillis()}" else "123412") {
                        if (isColor) {
                            it.setBackgroundResource(R.drawable.bg_so_black_a30_r24)
                        } else {
                            it.setBackgroundResource(R.drawable.bg_so_red_a30_r24)
                        }
                    }
                    testHandler?.sendEmptyMessageDelayed(100, 200)
                }
            }
        }
        testHandler?.sendEmptyMessageDelayed(100, 200)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy()")
        testHandler?.removeCallbacksAndMessages(null)
        testHandler = null
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart()")
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop()")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume()")
        damuView?.resume()
        barrageView?.resume()
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause()")
        damuView?.pause()
        barrageView?.pause()
    }
}