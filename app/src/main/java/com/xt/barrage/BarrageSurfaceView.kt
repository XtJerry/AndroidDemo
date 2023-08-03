package com.xt.test.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CopyOnWriteArrayList


class BarrageSurfaceView : SurfaceView, SurfaceHolder.Callback, Runnable {
    protected val TAG = this.javaClass.simpleName

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    private var showItemCount = 0//当前显示的数量
    private var itemHeight: Int = 0//Item高度
    var itemSpace: Int = dpToPx(10)//Item行间隔
    var itemColumnSpace: Int = dpToPx(10)//Item列间隔
    var maxLine = 5//最大行
    var cacheSize = 20//缓存View
    var isCover = false//true可以被覆盖,false不可以

    var refreshStep: Long = 1000 / 60//1s刷新n次
    var moveStep: Float = dpToPx(50) / (1000f / refreshStep)//1s/30dp
    private var viewList = mutableListOf<MutableList<BarrageMsg>>()//View保存
    private var cacheList = CopyOnWriteArrayList<BarrageMsg>()//缓存View
    private var cacheDataSize = 20
    private var queueList = ArrayBlockingQueue<BarrageMsg>(cacheDataSize)
    private var fps = 0f
    private var isShowFps = true
    private var isLog = true


    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        //handler.sendEmptyMessageDelayed(100, refreshStep)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacksAndMessages(null)
    }

    fun pause() {
        isStart = false
    }

    fun resume() {
        isStart = true
    }

    @Synchronized
    private fun clearHide() {
        for (list in viewList) {
            if (list.size > 0) {
                var firstView = list.get(0)
                if (firstView.x + firstView.width < 0) {
                    list.removeAt(0)
                    showItemCount--

                    removeView(firstView)
                    if (cacheList.size < cacheSize) {//添加到缓存
                        cacheList.add(firstView)
                    } else {
                        if (isLog) Log.d(TAG, "缓存View已满，丢弃")
                    }
                }
            }
        }
        if (showItemCount == 0) {
            if (isLog) Log.d(TAG, "暂停执行弹幕")
            handler.removeCallbacksAndMessages(null)
        }
    }


    private fun createView(msg: String, set: ((BarrageMsg) -> Unit)?): BarrageMsg {
        var barrageMsg: BarrageMsg? = null
        if (cacheList.size > 0) {
            barrageMsg = cacheList.removeFirst()
            if (isLog) Log.d(TAG, "使用缓存View创建,size=${cacheList.size}")
        } else {
            barrageMsg = BarrageMsg()
        }
        barrageMsg?.isInit = false
        barrageMsg?.msg = msg

        set?.invoke(barrageMsg!!)
        return barrageMsg!!
    }

    fun addItemView(msg: String, set: ((BarrageMsg) -> Unit)?) {
        if (!isStart) return //暂停，不处理

        if (viewList.size < maxLine) {
            synchronized(viewList) {
                if (viewList.size < maxLine) {
                    for (i in viewList.size until maxLine) {
                        viewList.add(mutableListOf())
                    }
                }
            }
        }
        var itemView: BarrageMsg?
        if (queueList.size < cacheDataSize) {
            itemView = createView(msg, set)
            queueList.add(itemView)
        } else {
            Log.w(TAG, "数据过多，直接已丢弃")
        }
    }


    private fun addViewToParent(i: Int, itemView: BarrageMsg) {
        showItemCount++
        if (showItemCount == 1) {
            handler.sendEmptyMessageDelayed(100, refreshStep)
            if (isLog) Log.d(TAG, "开始执行弹幕")
        }
        viewList.get(i).add(itemView)
    }

    private fun removeView(firstView: BarrageMsg) {
        showItemCount--
        if (cacheList.size < cacheSize) {
            cacheList.add(firstView)
        }
    }

    private fun dpToPx(dpValue: Int): Int {
        val density = resources.displayMetrics.density
        return Math.round(dpValue * density)
    }

    private fun dpToPx(dpValue: Float): Int {
        val density = resources.displayMetrics.density
        return Math.round(dpValue * density)
    }

    private fun init() {
        holder.addCallback(this)
    }

    private var barrageThread: Thread? = null
    private var surfaceHolder: SurfaceHolder? = null
    private var isRunning = true
    private var isStart = true
    override fun surfaceCreated(holder: SurfaceHolder) {
        if (isLog) Log.v(TAG, "surfaceCreated()")
        surfaceHolder = holder
        isRunning = true
        setZOrderOnTop(true)
        surfaceHolder?.setFormat(PixelFormat.TRANSPARENT)
        barrageThread = Thread(this)
        barrageThread?.start()
    }

    override fun surfaceChanged(p0: SurfaceHolder, p1: Int, p2: Int, p3: Int) {
        if (isLog) Log.v(TAG, "surfaceChanged()")
    }

    override fun surfaceDestroyed(p0: SurfaceHolder) {
        if (isLog) Log.v(TAG, "surfaceDestroyed()")
        try {
            isRunning = false
            barrageThread?.join()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    override fun run() {
        var startTime: Long = System.currentTimeMillis()
        var sleepTime: Long = 0
        var useTime: Long = 0
        var frpTime: Long = System.currentTimeMillis()
        var frpAdd: Int = 0
        while (isRunning) {
            startTime = System.currentTimeMillis()
            if (++frpAdd >= 10) {
                frpAdd = 0
                fps = 10000f / (System.currentTimeMillis() - frpTime)
                frpTime = System.currentTimeMillis()
                Log.d(TAG, "fps=$fps")
            }

            if (isStart && holder.surface.isValid) {
                var canvas: Canvas? = null
                try {
                    canvas = surfaceHolder?.lockCanvas()
                    if (canvas != null) {
                        drawBarrageAll(canvas)//// 在这里进行弹幕的绘制逻辑
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                if (canvas != null) surfaceHolder?.unlockCanvasAndPost(canvas)
            }
            useTime = System.currentTimeMillis() - startTime
            sleepTime = refreshStep - useTime
//
            try {
                if (sleepTime > 0) {
                    Thread.sleep(sleepTime)
//                    Log.v(TAG, "绘制时间:${useTime}ms")
                } else {
//                    Log.w(TAG, "绘制超时,$useTime")
                }
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
    }

    //    private var barrageMsg: BarrageMsg = BarrageMsg()
    private val textPaint = Paint()
    private val bgPaint = Paint()
    private fun drawBarrageAll(canvas: Canvas) {
        // 清空画布并绘制文本弹幕
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        for (i in 0 until Math.min(viewList.size, maxLine)) {
            var list = viewList.get(i)
            for (barrageMsg in list) {
                if (!barrageMsg.isInit) {
                    barrageMsg.isInit = true
                    //初始化
                    textPaint.textSize = barrageMsg.textSize
                    val textWidth: Float = textPaint.measureText(barrageMsg.msg)
                    val fontMetrics = textPaint.fontMetrics
                    var textHeight = fontMetrics.bottom - fontMetrics.top

                    barrageMsg.width = textWidth + barrageMsg.paddingHorizontal * 2
                    barrageMsg.height = textHeight + barrageMsg.paddingVertical * 2
                    barrageMsg.x = width.toFloat()
                    barrageMsg.y = i * (barrageMsg.height + itemSpace)

                }
                barrageMsg.x = barrageMsg.x - moveStep
                drawBarrage(canvas, barrageMsg, bgPaint, textPaint)
            }

            if (list.size > 0) {
                var firstView = list.get(0)
                if (firstView.x + firstView.width < 0) {
                    list.remove(firstView)
                    removeView(firstView)
                }
            }
            if (queueList.size > 0) {
                var endView: BarrageMsg? = null
                if (list.size > 0) {
                    endView = list.get(list.size - 1)
                }
                if (endView == null || endView.x + endView.width + itemColumnSpace <= this.width) {
                    var newItem = queueList.poll()
                    showItemCount++
                    list.add(newItem)
                }
            }
        }

        if (isShowFps) {
            textPaint.color = Color.parseColor("#FF0000")
            canvas.drawText(String.format("%.01f", fps), dpToPx(10).toFloat(), textPaint.textSize, textPaint)
        }
    }

    private fun drawBarrage(canvas: Canvas, barrageMsg: BarrageMsg, bgPaint: Paint, textPaint: Paint) {
//        canvas.drawColor(Color.TRANSPARENT) // 清空画布为黑色背景
        //绘制背景
        bgPaint.color = barrageMsg.background
//        bgPaint.alpha = barrageMsg.background.alpha
        if (barrageMsg.borderSize > 0) {
            bgPaint.style = Paint.Style.FILL_AND_STROKE
            bgPaint.strokeWidth = barrageMsg.borderSize
        } else {
            bgPaint.style = Paint.Style.FILL
        }
        bgPaint.isAntiAlias = true//抗锯齿
        var rect = RectF(barrageMsg.x, barrageMsg.y, barrageMsg.x + barrageMsg.width, barrageMsg.y + barrageMsg.height)
        canvas.drawRoundRect(rect, barrageMsg.radius, barrageMsg.radius, bgPaint)

        //绘制文本
        textPaint.color = barrageMsg.textColor
        textPaint.textSize = barrageMsg.textSize
        textPaint.isAntiAlias = true
        textPaint.textAlign = Paint.Align.CENTER
        val fontMetrics = textPaint.fontMetrics
        var textCenter = (barrageMsg.height - fontMetrics.bottom - fontMetrics.top) / 2
        canvas.drawText(barrageMsg.msg, barrageMsg.x + (barrageMsg.width) / 2, barrageMsg.y + textCenter, textPaint)
    }

    inner class BarrageMsg {
        var isInit: Boolean = false
        var msg: String = "弹幕"
        var x: Float = 0f
        var y: Float = 0f
        var height: Float = 0f
        var width: Float = 0f
        var textSize: Float = dpToPx(13).toFloat()
        var textColor: Int = Color.parseColor("#FFFFFF")
        var background: Int = Color.parseColor("#80d92e20")
        var borderColor: Int = Color.parseColor("#00FF00")
        var borderSize: Float = dpToPx(2).toFloat()
        var radius: Float = dpToPx(24).toFloat()
        var paddingVertical: Float = dpToPx(5).toFloat()
        var paddingHorizontal: Float = dpToPx(12).toFloat()
    }
}