package com.xt.test.view

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.FrameLayout
import android.widget.TextView
import com.xt.demo.R
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CopyOnWriteArrayList


/**
 * 简单弹幕View
 */
class BarrageView : FrameLayout {
    protected val TAG = this.javaClass.simpleName

    constructor(context: Context) : super(context) {
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {

    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {

    }

    private var showItemCount = 0//当前显示的数量
    private var itemHeight: Int = 0//Item高度
    var itemSpace: Int = dpToPx(8)//Item行间隔
    var itemColumnSpace: Int = dpToPx(8)//Item列间隔
    var maxLine = 5//最大行
    var cacheSize = 20//缓存View
    var isCover = false//true可以被覆盖,false不可以

    var refreshStep: Long = 1000 / 60//1s刷新n次
    var moveStep: Float = dpToPx(50) / (1000f / refreshStep)//1s/30dp
    private var viewList = mutableListOf<MutableList<View>>()//View保存
    private var cacheList = CopyOnWriteArrayList<View>()//缓存View
    var isCacheData = true//使用缓存,数据过多，直接使用缓存
    private var cacheDataSize = 20
    private var queueList = ArrayBlockingQueue<View>(cacheDataSize)
    private var isLog = true
    private var isStart = true //执行中
    private val WHAT_FRESH = 100

    private var handler = object : Handler(Looper.getMainLooper()) {
        var startTime = System.currentTimeMillis()
        var userTime = 0L
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            if (msg.what == WHAT_FRESH) {
                startTime = System.currentTimeMillis()
                for (i in 0 until Math.min(viewList.size, maxLine)) {
                    var list = viewList.get(i)
                    for (view in list) {
                        view.x = view.x - moveStep
                    }

                    //处理缓存
                    if (isCacheData && queueList.size > 0) {
                        var endView: View? = null
                        if (list.size != 0) {//安全判断，不会发送
                            endView = list.get(list.size - 1)
                        }

                        if (endView == null || (endView.x + endView.width + itemColumnSpace) < this@BarrageView.width) {
                            if (isLog) Log.d(TAG, "从队列拉取缓存,size=${queueList.size}")
                            var itemView = queueList.poll()
                            addViewToParent(i, itemView)
                        }
                    }
                }
                clearHide()
                userTime = System.currentTimeMillis() - startTime
                if (isStart) {
                    removeMessages(WHAT_FRESH)

                    if ((refreshStep - userTime) < 0) {
                        sendEmptyMessage(WHAT_FRESH)
//                        if (isLog) Log.w(TAG, "处理超时,$userTime")
                    } else {
                        sendEmptyMessageDelayed(WHAT_FRESH, refreshStep - userTime)
//                        if (isLog) Log.v(TAG, "下次执行,${refreshStep - userTime}")
                    }
                }
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        //handler.sendEmptyMessageDelayed(WHAT_FRESH, refreshStep)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacksAndMessages(null)
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
            handler.removeMessages(WHAT_FRESH)
        }
    }

    private fun createView(msg: String, set: ((TextView) -> Unit)?): View {
        var itemView: View? = null
        if (cacheList.size > 0) {
            itemView = cacheList.removeFirst()
            if (isLog) Log.d(TAG, "使用缓存View创建,size=${cacheList.size}")
        } else {
            itemView = LayoutInflater.from(context).inflate(R.layout.item_barrage_view, null)
        }

        var textView = itemView.findViewById<TextView>(R.id.barrageTxt)
        textView.text = msg
        set?.invoke(textView)
        return itemView
    }

    @Synchronized
    fun addItemView(msg: String, set: ((TextView) -> Unit)?) {
        if (!isStart) return //暂停，不处理
        var itemView: View?

        //初始化ViewList
        if (viewList.size < maxLine) {
            for (i in viewList.size until maxLine) {
                viewList.add(mutableListOf())
            }
        }

        if (false) {//直接添加，使用缓存时可能距离有问题，会重叠
            var isAdd = false
            for (i in 0 until Math.min(viewList.size, maxLine)) {
                var list = viewList.get(i)
                var endView: View? = null
                if (list.size != 0) {
                    endView = list.get(list.size - 1)
                }

                if (endView == null || (endView.x + endView.width + itemColumnSpace) < this.width) {
                    itemView = createView(msg, set)
                    addViewToParent(i, itemView)
                    isAdd = true
                    break
                }
            }
            if (!isAdd) {
                if (isCover) {//叠加
                    itemView = createView(msg, set)
                    addViewToParent(0, itemView)
                } else if (isCacheData && queueList.size < cacheDataSize) {
                    itemView = createView(msg, set)
                    queueList.add(itemView)
                    if (isLog) Log.d(TAG, "添加到队列")
                } else {
                    Log.w(TAG, "数据过多，直接已丢弃")
                }
            }
        } else {//使用队列添加
            if (queueList.size < cacheDataSize) {
                itemView = createView(msg, set)
                queueList.add(itemView)
                showItemCount++
                if (showItemCount == 1) {
                    handler.sendEmptyMessage(WHAT_FRESH)
                    if (isLog) Log.d(TAG, "开始执行弹幕")
                }
            }
        }

    }


    private fun addViewToParent(i: Int, itemView: View) {
        if (itemView.parent != null) {
            (itemView.parent as ViewGroup).removeView(itemView)
        }

        if (itemHeight <= 0) {
            itemView.viewTreeObserver.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    itemHeight = itemView.height
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {// 移除监听器以避免重复回调
                        itemView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    } else {// 兼容旧版本 Android
                        itemView.viewTreeObserver.removeGlobalOnLayoutListener(this)
                    }
                }
            })
        }
        var params = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        addView(itemView, params)

        viewList.get(i).add(itemView)
        itemView.x = this.width.toFloat()

        itemView.y = (i * (itemHeight + itemSpace)).toFloat()
    }

    fun resume() {
        if (showItemCount > 0 && !isStart) handler.sendEmptyMessageDelayed(WHAT_FRESH, 0)//!isStart 防止重复更新
        isStart = true

    }

    fun pause() {
        isStart = false
        handler.removeMessages(WHAT_FRESH)
    }

    @Synchronized
    fun clearAll() {
        for (list in viewList) {
            for (firstView in list) {
                removeView(firstView)
                if (cacheList.size < cacheSize) {//添加到缓存
                    cacheList.add(firstView)
                } else {
                    if (isLog) Log.d(TAG, "缓存View已满，丢弃")
                }
            }
            list.clear()
        }
        showItemCount = 0
    }

    fun isStart(): Boolean {
        return isStart
    }

    private fun dpToPx(dpValue: Int): Int {
        val density = resources.displayMetrics.density
        return Math.round(dpValue * density)
    }
}