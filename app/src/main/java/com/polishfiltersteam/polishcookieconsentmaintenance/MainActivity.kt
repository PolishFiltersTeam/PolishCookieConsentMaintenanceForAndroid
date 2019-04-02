//Copyright (C) 2019 Polish Filters Team
//
//This program is free software: you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation, either version 3 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program.  If not, see <https://www.gnu.org/licenses/>.

package com.polishfiltersteam.polishcookieconsentmaintenance

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment

import com.obsez.android.lib.filechooser.ChooserDialog
import android.view.View
import androidx.appcompat.app.AlertDialog
import kotlinx.android.synthetic.main.activity_main.*
import com.android.volley.Request
import com.android.volley.Response.ErrorListener
import com.android.volley.Response.Listener
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.g00fy2.versioncompare.Version
import com.tonyodev.fetch2.*
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.nio.channels.FileChannel
import java.nio.charset.Charset
import com.tonyodev.fetch2core.Func
import com.tonyodev.fetch2.Request as fetchRequest
import com.tonyodev.fetch2.Download
import com.tonyodev.fetch2.FetchListener
import com.tonyodev.fetch2core.DownloadBlock
import org.jetbrains.annotations.NotNull
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import androidx.annotation.NonNull
import ir.mahdi.mzip.zip.ZipArchive
import java.text.DecimalFormat
import java.util.*


class MainActivity : AppCompatActivity() {

    lateinit var mTTS:TextToSpeech

    fun getInstallPath(): String? {
        val settings = getSharedPreferences("PCCM", Context.MODE_PRIVATE)
        val storage: String? = Environment.getExternalStorageDirectory().absolutePath

        return if (settings.contains("pathInstallExt")) {
            settings.getString("pathInstallExt", "")
        }
        else {
            storage + "/" + getString(R.string.extensions) + "/PolishCookieConsent"
        }
    }

    private fun getUpdateMessage(): String? {
        val manifest = File(getInstallPath() + "/manifest.json")
        return if(manifest.exists()) {
            getString(R.string.updated)
        }
        else {
            getString(R.string.downloadedExtracted)
        }
    }

    @NonNull fun getDownloadSpeedString(@NonNull context: Context, downloadedBytesPerSecond: Long): String? {
        if (downloadedBytesPerSecond < 0) {
            return "";
        }
        var kb: Double = downloadedBytesPerSecond.toDouble() / 1000.toDouble()
        var mb: Double = kb / (1000.toDouble())
        val decimalFormat = DecimalFormat(".##")
        return when {
            mb >= 1 -> context.getString(R.string.download_speed_mb, decimalFormat.format(mb))
            kb >= 1 -> context.getString(R.string.download_speed_kb, decimalFormat.format(kb))
            else -> context.getString(R.string.download_speed_bytes, downloadedBytesPerSecond)
        }
    }

     @NonNull fun getETAString(@NonNull context: Context, etaInMilliSeconds: Long): String? {
        if (etaInMilliSeconds < 0) {
            return "";
        }
        var seconds: Int = (etaInMilliSeconds.toInt() / 1000)
        var hours: Long = seconds.toLong() / 3600
        seconds -= hours.toInt() * 3600
        var minutes: Long = seconds.toLong() / 60
        seconds -= minutes.toInt() * 60

         return when {
             hours > 0 -> getString(R.string.remaining) + " " + context.getString(R.string.download_eta_hrs, hours, minutes, seconds)
             minutes > 0 -> getString(R.string.remaining) + " " + context.getString(R.string.download_eta_min, minutes, seconds)
             else -> getString(R.string.remaining) + " " + context.getString(R.string.download_eta_sec, seconds)
         }
}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        changePathLbl.text = getInstallPath()

        mTTS = TextToSpeech(applicationContext, OnInitListener { status ->
            if (status != TextToSpeech.ERROR){
                //if there is no error then set language
                mTTS.language = Locale.getDefault()
            }
        })

        val manifest = File(getInstallPath() + "/manifest.json")
        if(!manifest.exists()) {
            downloadBtn.text = getString(R.string.downloadExtract)
        }

    }


    fun onChangePathBtnClicked(view: View) {
        ChooserDialog(this@MainActivity, R.style.FileChooserStyle_Dark)
                .withResources(R.string.choose_folder, R.string.choose, R.string.cancel)
                .withFilter(true, false)
                .enableOptions(true)
                .withOptionResources(R.string.new_folder, R.string.delete, R.string.cancel, R.string.new_folder_ok)
                .withChosenListener {
                    installPath, _ ->
                    val context = this@MainActivity
                    val sharedPref = context.getSharedPreferences("PCCM", Context.MODE_PRIVATE)
                    with (sharedPref.edit()) {
                        putString("pathInstallExt", installPath)
                        commit()
                    }
                    changePathLbl.text = installPath
                    val manifest = File(getInstallPath() + "/manifest.json")
                    if(manifest.exists()) {
                        downloadBtn.text = getString(R.string.update)
                    }
                    else {
                        downloadBtn.text = getString(R.string.downloadExtract)
                    }
                }
                .build()
                .show()

    }

    fun onDownloadBtnClicked(view: View)
    {
        val requestQueue = Volley.newRequestQueue(this);
        val url = "https://api.github.com/repos/PolishFiltersTeam/PolishCookieConsent/releases/latest"
        val request = JsonObjectRequest(Request.Method.GET, url, null, Listener { response ->
            val newVersionStr = response.getString("tag_name").replace("v", "")
            val newVersion = Version(newVersionStr)

            val manifest = File(getInstallPath() + "/manifest.json")
            val oldVersion = if (manifest.exists()) {
                val fileIS = FileInputStream(manifest)
                val fileCh = fileIS.channel
                val mappedBB = fileCh.map(FileChannel.MapMode.READ_ONLY, 0, fileCh.size())
                val jsonStr = Charset.defaultCharset().decode(mappedBB).toString()
                val jsonObject = JSONObject(jsonStr)
                Version(jsonObject.optString("version"))
            } else {
                Version("0")
            }


            val pathDownloadExtStr = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString() + "/PolishCookieConsent"
            val pathDownloadExt = File(pathDownloadExtStr)
            val pathDownloadExtFile = "$pathDownloadExtStr/PolishCookieConsentChromium.zip"

            val updatedMsg = getUpdateMessage()

            if(newVersion.isHigherThan(oldVersion)) {
                if(!pathDownloadExt.exists()) {
                    pathDownloadExt.mkdir()
                }
                val fetchConfiguration = FetchConfiguration.Builder(this)
                        .setDownloadConcurrentLimit(2)
                        .setNotificationManager(DefaultFetchNotificationManager(this))
                        .enableRetryOnNetworkGain(true)
                        .enableAutoStart(false)
                        .build()
                val fetch = Fetch.getInstance(fetchConfiguration);


                val downloadURL = "https://github.com/PolishFiltersTeam/PolishCookieConsent/releases/download/v$newVersionStr/PolishCookieConsent_chromium.zip"

                val requestPCC = fetchRequest(downloadURL, pathDownloadExtFile)
                requestPCC.priority = Priority.HIGH
                requestPCC.networkType = NetworkType.ALL

                fetch.enqueue(requestPCC, Func {
                }, Func { error ->
                    AlertDialog.Builder(this)
                            .setTitle(R.string.error)
                            .setMessage(error.toString())
                            .setPositiveButton("OK", null)
                            .show()
                })

                val fetchListener = object: FetchListener {

                    override fun onQueued(@NotNull download: Download, waitingOnNetwork: Boolean) {
                        progressBar.progress = 0
                        labelPerc.text = "0%"
                        labelStatus.text = ""
                        labelRemaining.text =""
                    }

                    override fun onCompleted(@NotNull download: Download) {
                        fetch.remove(requestPCC.id)
                        labelStatus.text = getString(R.string.extracting)
                        ZipArchive.unzip(pathDownloadExtFile, getInstallPath(), "")
                        pathDownloadExt.deleteRecursively()
                        AlertDialog.Builder(this@MainActivity)
                                .setTitle(R.string.info)
                                .setMessage(updatedMsg)
                                .setPositiveButton("OK", null)
                                .show()
                        mTTS.speak(updatedMsg, TextToSpeech.QUEUE_FLUSH, null, null)
                        downloadBtn.text = getString(R.string.update)
                        labelStatus.text = getString(R.string.ready)
                    }

                    override fun onError(@NotNull download: Download, error: Error, throwable: Throwable?) {
                        AlertDialog.Builder(this@MainActivity)
                                .setTitle(R.string.info)
                                .setMessage(download.error.toString())
                                .setPositiveButton("OK", null)
                                .show()
                        fetch.remove(requestPCC.id)
                        pathDownloadExt.deleteRecursively()
                        labelStatus.text = getString(R.string.error)
                    }

                    override fun onProgress(@NotNull download: Download, etaInMilliSeconds: Long, downloadedBytesPerSecond: Long) {
                        val progress = download.progress
                        progressBar.progress = progress
                        labelPerc.text = "$progress%"
                        labelSpeed.text = getDownloadSpeedString(this@MainActivity, downloadedBytesPerSecond)
                        labelRemaining.text = getETAString(this@MainActivity, etaInMilliSeconds)
                    }

                    override fun onPaused(download: Download) {
                        labelStatus.text = getString(R.string.paused)
                    }

                    override fun onResumed(download: Download) {
                        labelStatus.text = getString(R.string.downloading)
                    }

                    override fun onCancelled(download: Download) {
                        fetch.remove(requestPCC.id)
                        pathDownloadExt.deleteRecursively()
                        labelStatus.text = getString(R.string.canceled)
                        labelRemaining.text = ""
                        progressBar.progress = 0
                        labelPerc.text = "0%"
                        labelSpeed.text = "0 MB/S"
                    }

                    override fun onRemoved(download: Download) {

                    }
                    override fun onDeleted(download: Download) {

                    }

                    override fun onStarted(download: Download, downloadBlocks: List<DownloadBlock>, totalBlocks: Int) {
                        labelStatus.text = getString(R.string.downloading)
                    }

                    override fun onWaitingNetwork(download: Download) {
                        labelStatus.text = getString(R.string.waiting)
                    }

                    override fun onAdded(download: Download) {

                    }

                    override fun onDownloadBlockUpdated(download: Download, downloadBlock: DownloadBlock, totalBlocks: Int) {

                    }

                }
                fetch.addListener(fetchListener)
            }
            else {
                AlertDialog.Builder(this)
                        .setTitle(R.string.info)
                        .setMessage(R.string.noNewVersion)
                        .setPositiveButton("OK", null)
                        .show()
            }

        }, ErrorListener { error ->
            AlertDialog.Builder(this)
                    .setTitle(R.string.error)
                    .setMessage(error.toString())
                    .setPositiveButton("OK", null)
                    .show()
        })
        requestQueue.add(request)
    }

}

