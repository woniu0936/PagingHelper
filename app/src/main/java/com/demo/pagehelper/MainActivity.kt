package com.demo.pagehelper

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.demo.pagehelper.flowdata.FlowDataSourceActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        fullScreen(findViewById(R.id.main))
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("FATAL_CRASH", "🔥 全局捕获到异常 !!! 🔥", throwable)
            // 可以在这里把异常写入文件，或者直接打印
        }

        findViewById<Button>(R.id.btn_linear).setOnClickListener {
            Intent(this, LinearDemoActivity::class.java).apply {
                startActivity(this)
            }
        }
        findViewById<Button>(R.id.btn_grid).setOnClickListener {
            Intent(this, GridLayoutDemoActivity::class.java).apply {
                startActivity(this)
            }
        }
        findViewById<Button>(R.id.btn_staggered).setOnClickListener {
            Intent(this, StaggeredGridDemoActivity::class.java).apply {
                startActivity(this)
            }
        }
        findViewById<Button>(R.id.btn_flow).setOnClickListener {
            Intent(this, FlowDataSourceActivity::class.java).apply {
                startActivity(this)
            }
        }

    }
}

fun AppCompatActivity.fullScreen(rootView: View) {
    enableEdgeToEdge()
    ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
        insets
    }
}