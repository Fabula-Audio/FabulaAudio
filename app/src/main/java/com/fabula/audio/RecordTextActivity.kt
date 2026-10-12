package com.fabula.audio

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.text.InputType
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.util.Log
import android.util.Xml
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream

class RecordTextActivity : Activity() {
    companion object {
        private const val REQ_TEXT_FILE = 100
        private const val REQ_FOLDER = 201
        private const val REQ_AUDIO = 301
        private const val PREFS = "fabula_storage"
        private const val TAG = "FABULA_PREP"
    }

    private data class Row(var text: String, var selected: Boolean)
    private data class FragmentItem(var text: String, var path: String? = null, var recorded: Boolean = false)

    private val rows = mutableListOf<Row>()
    private val fragments = mutableListOf<FragmentItem>()
    private lateinit var root: LinearLayout
    private lateinit var listContainer: LinearLayout
    private lateinit var bottomButton: Button
    private lateinit var selectAll: CheckBox
    private var updatingSelection = false
    private var showingFragments = false
    private var pendingMerge = false
    private var pendingSaveTitle = ""
    private var pendingSaveComment = ""

    private var recorder: StereoWavRecorder? = null
    private var player: MediaPlayer? = null
    private var recordingIndex = -1
    private var recordingFile: File? = null
    private val handler = Handler(Looper.getMainLooper())
    private var amplitudeProgress: ProgressBar? = null

    private val amplitudeRunnable = object : Runnable {
        override fun run() {
            if (recordingIndex < 0) return
            val progress = try {
                recorder?.maxAmplitude?.coerceIn(0, 32767) ?: 0
            } catch (_: Exception) {
                0
            }
            amplitudeProgress?.progress = progress
            handler.postDelayed(this, 100L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildRoot())
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQ_AUDIO)
        }
    }

    private fun buildRoot(): LinearLayout {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.fabula_background))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12.dp(), 4.dp(), 8.dp(), 4.dp())
        }
        val title = TextView(this).apply {
            text = "Записать аудио-заготовку фабулы по тексту"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(getColor(R.color.fabula_text))
        }
        header.addView(title, LinearLayout.LayoutParams(0, 56.dp(), 1f))
        root.addView(header)

        showTextSelectionContent()
        return root
    }

    private fun showTextSelectionContent() {
        showingFragments = false
        root.removeViews(1, root.childCount - 1)

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12.dp(), 4.dp(), 12.dp(), 4.dp())
        }
        val load = Button(this).apply {
            text = "Загрузить файл (.txt, .docx)"
            isAllCaps = false
            textSize = 13f
        }
        UiStyles.styleActionButton(load)
        load.setOnClickListener { loadTextFile() }
        val paste = Button(this).apply {
            text = "Вставить из буфера обмена"
            isAllCaps = false
            textSize = 13f
        }
        UiStyles.styleActionButton(paste)
        paste.setOnClickListener { pasteClipboard() }
        buttons.addView(load, LinearLayout.LayoutParams(0, -2, 1f))
        buttons.addView(paste, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(buttons)

        val hint = TextView(this).apply {
            text = "Коснитесь строки или маркера слева, чтобы выбрать её для записи."
            setTextColor(getColor(R.color.fabula_muted))
            setPadding(16.dp(), 6.dp(), 16.dp(), 8.dp())
        }
        root.addView(hint)

        selectAll = CheckBox(this).apply {
            text = "Выбрать всё"
            setTextColor(getColor(R.color.fabula_text))
            setPadding(16.dp(), 0, 16.dp(), 0)
            setOnCheckedChangeListener { _, checked ->
                if (!updatingSelection) {
                    rows.forEach { it.selected = checked }
                    renderRows()
                }
            }
        }
        root.addView(selectAll)

        val scroll = ScrollView(this)
        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.dp(), 4.dp(), 12.dp(), 12.dp())
        }
        scroll.addView(listContainer)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        bottomButton = Button(this).apply {
            text = "Далее"
            isAllCaps = false
            isEnabled = rows.any { it.selected }
        }
        UiStyles.styleActionButton(bottomButton)
        bottomButton.setOnClickListener { buildFragmentsFromRows() }
        root.addView(bottomButton, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(12.dp(), 4.dp(), 12.dp(), 12.dp())
        })
        renderRows()
    }

    private fun showFragmentContent() {
        showingFragments = true
        root.removeViews(1, root.childCount - 1)

        val hint = TextView(this).apply {
            text = "Нажимайте кнопки прямо в строке. Долгое нажатие на фрагмент — удалить."
            setTextColor(getColor(R.color.fabula_muted))
            setPadding(16.dp(), 4.dp(), 16.dp(), 8.dp())
        }
        root.addView(hint)

        val scroll = ScrollView(this)
        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.dp(), 4.dp(), 12.dp(), 12.dp())
        }
        scroll.addView(listContainer)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        bottomButton = Button(this).apply {
            text = "Склеить и сохранить"
            isAllCaps = false
            isEnabled = fragments.any { it.path?.let(::File)?.exists() == true }
        }
        UiStyles.styleActionButton(bottomButton)
        bottomButton.setOnClickListener { mergeAndSave() }
        root.addView(bottomButton, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(12.dp(), 4.dp(), 12.dp(), 12.dp())
        })
        renderFragments()
    }

    private fun loadTextFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/plain", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
        }
        try {
            startActivityForResult(intent, REQ_TEXT_FILE)
        } catch (e: Exception) {
            Toast.makeText(this, "Не удалось открыть выбор файла.", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Activity Result API kept for project compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        when (requestCode) {
            REQ_TEXT_FILE -> readSelectedTextFile(data.data!!)
            REQ_FOLDER -> handleFolderPicked(data)
        }
    }

    private fun readSelectedTextFile(uri: Uri) {
        try {
            val name = queryDisplayName(uri).lowercase(Locale.ROOT)
            val mime = contentResolver.getType(uri).orEmpty().lowercase(Locale.ROOT)
            val text = when {
                name.endsWith(".txt") || mime == "text/plain" -> readPlainText(uri)
                name.endsWith(".docx") || mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> readDocx(uri)
                else -> throw IllegalArgumentException("Формат не распознан. Поддерживаются только .txt и .docx.")
            }
            if (text.isBlank()) throw IllegalArgumentException("Файл не содержит текста.")
            setSourceText(text)
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: "Не удалось прочитать файл.", Toast.LENGTH_LONG).show()
        }
    }

    private fun readPlainText(uri: Uri): String {
        return contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: throw IllegalStateException("Не удалось прочитать файл.")
    }

    private fun readDocx(uri: Uri): String {
        val input = contentResolver.openInputStream(uri) ?: throw IllegalStateException("Не удалось открыть файл .docx.")
        input.use { source ->
            ZipInputStream(source).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name == "word/document.xml") {
                        return extractDocxText(zip)
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        throw IllegalArgumentException("В файле .docx не найден текст документа.")
    }

    private fun extractDocxText(input: InputStream): String {
        val parser = Xml.newPullParser()
        parser.setInput(input, "UTF-8")
        val out = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (parser.name.substringAfter(':') == "t") {
                        out.append(parser.nextText())
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name.substringAfter(':')) {
                        "p" -> out.append('\n')
                        "tab" -> out.append(' ')
                    }
                }
            }
            event = parser.next()
        }
        return out.toString()
    }

    private fun pasteClipboard() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip: ClipData? = clipboard.primaryClip
        val text = clip?.getItemAt(0)?.coerceToText(this)?.toString()
        if (text.isNullOrBlank()) {
            Toast.makeText(this, "Буфер обмена пуст.", Toast.LENGTH_SHORT).show()
            return
        }
        setSourceText(text)
    }

    private fun setSourceText(source: String) {
        rows.clear()
        fragments.clear()
        source.replace("\r\n", "\n").replace('\r', '\n').split('\n').forEach { raw ->
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return@forEach
            val marked = trimmed.startsWith("*")
            val clean = if (trimmed.length >= 2 && trimmed.startsWith("*") && trimmed.endsWith("*")) {
                trimmed.substring(1, trimmed.length - 1).trim()
            } else if (marked) {
                trimmed.substring(1).trim()
            } else trimmed
            if (clean.isNotEmpty()) rows.add(Row(clean, marked))
        }
        showTextSelectionContent()
    }

    private fun renderRows() {
        listContainer.removeAllViews()
        rows.forEachIndexed { index, row ->
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(8.dp(), 4.dp(), 8.dp(), 4.dp())
                setBackgroundColor(if (row.selected) getColor(R.color.fabula_card) else getColor(R.color.fabula_background))
            }
            val check = CheckBox(this).apply {
                isChecked = row.selected
                contentDescription = "Выбрать строку ${index + 1}"
                isClickable = false
            }
            val text = TextView(this).apply {
                this.text = row.text
                setTextColor(getColor(R.color.fabula_text))
                textSize = 16f
                setPadding(8.dp(), 8.dp(), 8.dp(), 8.dp())
            }
            box.addView(check, LinearLayout.LayoutParams(48.dp(), -2))
            box.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
            val toggle = {
                row.selected = !row.selected
                renderRows()
            }
            box.setOnClickListener { toggle() }
            text.setOnClickListener { toggle() }
            listContainer.addView(box, LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(0, 2.dp(), 0, 2.dp())
            })
        }
        updatingSelection = true
        selectAll.isEnabled = rows.isNotEmpty()
        selectAll.isChecked = rows.isNotEmpty() && rows.all { it.selected }
        updatingSelection = false
        bottomButton.isEnabled = rows.any { it.selected }
    }

    private fun buildFragmentsFromRows() {
        fragments.clear()
        rows.forEach { row ->
            if (row.selected) {
                fragments.add(FragmentItem(row.text.trim()))
            }
        }
        if (fragments.isEmpty()) return
        showFragmentContent()
    }

    private fun renderFragments() {
        listContainer.removeAllViews()
        fragments.forEachIndexed { index, item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(10.dp(), 8.dp(), 10.dp(), 8.dp())
                setBackgroundColor(getColor(R.color.fabula_card))
            }
            val title = TextView(this).apply {
                text = "${index + 1}. ${truncate(item.text)}"
                textSize = 15f
                setTextColor(getColor(R.color.fabula_text))
            }
            val status = TextView(this).apply {
                text = if (index == recordingIndex) "идёт запись…" else if (item.recorded) "записан" else "не записан"
                setTextColor(if (index == recordingIndex) getColor(R.color.fabula_warning_text) else if (item.recorded) getColor(R.color.fabula_mode_border) else getColor(R.color.fabula_status_unrecorded))
                setPadding(0, 3.dp(), 0, 3.dp())
            }
            row.addView(title)
            row.addView(status)

            if (index == recordingIndex) {
                val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 32767
                    progress = 0
                    amplitudeProgress = this
                }
                row.addView(progress, LinearLayout.LayoutParams(-1, 12.dp()).apply { setMargins(0, 2.dp(), 0, 4.dp()) })
            }

            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val record = smallButton(if (index == recordingIndex) "Стоп" else "Записать")
            val listen = smallButton("Прослушать")
            val delete = smallButton("Удалить")
            val hasRecording = item.path?.let(::File)?.exists() == true
            record.isEnabled = index == recordingIndex || recordingIndex < 0
            listen.isEnabled = recordingIndex < 0 && hasRecording
            delete.isEnabled = recordingIndex < 0 && hasRecording
            record.alpha = if (record.isEnabled) 1.0f else 0.5f
            listen.alpha = if (listen.isEnabled) 1.0f else 0.5f
            delete.alpha = if (delete.isEnabled) 1.0f else 0.5f

            record.setOnClickListener {
                if (index == recordingIndex) stopRecording() else startRecording(index)
            }
            listen.setOnClickListener { listenFragment(index) }
            delete.setOnClickListener { confirmDelete(index) }

            actions.addView(record, LinearLayout.LayoutParams(0, 40.dp(), 1f).apply { setMargins(1.dp(), 0, 1.dp(), 0) })
            actions.addView(listen, LinearLayout.LayoutParams(0, 40.dp(), 1f).apply { setMargins(1.dp(), 0, 1.dp(), 0) })
            actions.addView(delete, LinearLayout.LayoutParams(0, 40.dp(), 1f).apply { setMargins(1.dp(), 0, 1.dp(), 0) })
            row.addView(actions)
            listContainer.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(0, 3.dp(), 0, 3.dp())
            })
        }
        bottomButton.isEnabled = recordingIndex < 0 && fragments.any { it.path?.let(::File)?.exists() == true }
    }

    private fun smallButton(label: String): Button = Button(this).apply {
        text = label
        textSize = 10f
        isAllCaps = false
        minHeight = 40.dp()
        minimumHeight = 40.dp()
        setPadding(1.dp(), 0, 1.dp(), 0)
        UiStyles.styleActionButton(this)
    }

    private fun startRecording(index: Int) {
        if (recordingIndex >= 0) {
            Toast.makeText(this, "Сначала остановите текущую запись.", Toast.LENGTH_SHORT).show()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQ_AUDIO)
            return
        }
        stopPlayback()
        fragments[index].path?.let { File(it).delete() }
        fragments[index].path = null
        fragments[index].recorded = false
        recordingFile?.delete()
        val file = File(cacheDir, "fabula_fragment_${System.currentTimeMillis()}.wav")
        try {
            val r = StereoWavRecorder()
            recorder = r
            r.start(file)
            recordingFile = file
            recordingIndex = index
            renderFragments()
            handler.post(amplitudeRunnable)
        } catch (e: Exception) {
            releaseRecorder()
            file.delete()
            recordingFile = null
            recordingIndex = -1
            Toast.makeText(this, "Ошибка записи: ${e.message ?: "неизвестная ошибка"}", Toast.LENGTH_LONG).show()
            renderFragments()
        }
    }

    private fun stopRecording() {
        if (recordingIndex < 0) return
        val index = recordingIndex
        recordingIndex = -1
        handler.removeCallbacks(amplitudeRunnable)
        try { recorder?.stop() } catch (_: Exception) { recordingFile?.delete(); recordingFile = null }
        releaseRecorder()
        amplitudeProgress = null
        val file = recordingFile
        recordingFile = null
        if (file?.exists() == true && file.length() > 44L) {
            fragments[index].path = file.absolutePath
            fragments[index].recorded = true
            Toast.makeText(this, "Фрагмент ${index + 1} записан.", Toast.LENGTH_SHORT).show()
        } else {
            file?.delete()
            fragments[index].path = null
            fragments[index].recorded = false
            Toast.makeText(this, "Запись не получена.", Toast.LENGTH_SHORT).show()
        }
        renderFragments()
    }

    private fun releaseRecorder() {
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
    }

    private fun listenFragment(index: Int) {
        val file = fragments[index].path?.let(::File)
        if (file == null || !file.exists()) {
            if (index == recordingIndex) {
                Toast.makeText(this, "Сначала остановите запись.", Toast.LENGTH_SHORT).show()
            }
            return
        }
        stopPlayback()
        try {
            val mp = MediaPlayer()
            player = mp
            mp.setDataSource(file.absolutePath)
            mp.setOnCompletionListener { stopPlayback() }
            mp.prepare()
            mp.start()
        } catch (e: Exception) {
            Toast.makeText(this, "Не удалось прослушать запись: ${e.message ?: "ошибка"}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopPlayback() {
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
    }

    private fun confirmDelete(index: Int) {
        if (index == recordingIndex) {
            Toast.makeText(this, "Сначала остановите запись.", Toast.LENGTH_SHORT).show()
            return
        }
        if (fragments[index].path?.let(::File)?.exists() != true) return
        val dialog = AlertDialog.Builder(this)
            .setTitle("Удалить запись?")
            .setMessage("Запись фрагмента ${index + 1} будет удалена. Сам текст фрагмента останется.")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Удалить") { _, _ ->
                fragments[index].path?.let { File(it).delete() }
                fragments[index].path = null
                fragments[index].recorded = false
                renderFragments()
            }
            .create()
        dialog.show()
        UiStyles.styleDialogButtons(dialog)
    }

    private fun mergeAndSave() {
        if (recordingIndex >= 0) {
            Toast.makeText(this, "Сначала остановите запись.", Toast.LENGTH_SHORT).show()
            return
        }
        val files = fragments.mapNotNull { it.path?.let(::File)?.takeIf(File::exists) }
        if (files.isEmpty()) return
        showSaveDialog()
    }

    private fun showSaveDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp(), 4.dp(), 24.dp(), 4.dp())
        }
        val titleInput = EditText(this).apply {
            hint = "Например: Утренний настрой"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(true)
        }
        val commentInput = EditText(this).apply {
            hint = "Комментарий к записи"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            maxLines = 4
            gravity = Gravity.TOP
        }
        container.addView(titleInput, LinearLayout.LayoutParams(-1, -2))
        container.addView(commentInput, LinearLayout.LayoutParams(-1, 100.dp()).apply { setMargins(0, 10.dp(), 0, 0) })

        val dialog = AlertDialog.Builder(this)
            .setTitle("Сохранение записи")
            .setView(container)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Далее", null)
            .create()
        dialog.setOnShowListener {
            UiStyles.styleDialogButtons(dialog)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                pendingSaveTitle = titleInput.text.toString().trim()
                pendingSaveComment = commentInput.text.toString().trim()
                dialog.dismiss()
                pendingMerge = true
                chooseFolder()
            }
        }
        dialog.show()
    }

    private fun performMerge(tree: Uri, files: List<File>) {
        bottomButton.isEnabled = false
        Thread {
            var listFile: File? = null
            var output: File? = null
            try {
                listFile = File(cacheDir, "fabula_concat_${System.currentTimeMillis()}.txt")
                listFile.writeText(files.joinToString("\n") { "file '${escapeConcatPath(it.absolutePath)}'" }, Charsets.UTF_8)
                output = File(cacheDir, "fabula_merge_${timestamp()}.wav")
                val command = "-y -f concat -safe 0 -i ${q(listFile)} -c:a pcm_s16le -ar 44100 -ac 2 ${q(output)}"
                Log.i(TAG, "FABULA_PREP concat: $command")
                val session = FFmpegKit.execute(command)
                check(ReturnCode.isSuccess(session.returnCode)) { "Ошибка FFmpeg при склейке" }
                check(output.exists() && output.length() > 0L) { "FFmpeg не создал результат" }
                val audioName = buildAudioFileName()
                saveRecordingToSaf(tree, output, audioName)
                val folderName = friendlyFolderName(tree)
                val savedDescription = buildDescription()
                saveDescriptionToSaf(tree, audioName.removeSuffix(".wav") + ".txt", savedDescription)
                runOnUiThread {
                    val dialog = AlertDialog.Builder(this)
                        .setTitle("Заготовка сохранена")
                        .setMessage("Запись сохранена в папку «$folderName».\n\nИмя файла: $audioName")
                        .setNegativeButton("Закрыть", null)
                        .setPositiveButton("Выход на главную") { _, _ -> finish() }
                        .create()
                    dialog.show()
                    UiStyles.styleDialogButtons(dialog)
                    bottomButton.isEnabled = fragments.any { it.path?.let(::File)?.exists() == true }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка склейки/сохранения", e)
                runOnUiThread {
                    Toast.makeText(this, "Не удалось сохранить заготовку: ${e.message ?: "неизвестная ошибка"}", Toast.LENGTH_LONG).show()
                    bottomButton.isEnabled = fragments.any { it.path?.let(::File)?.exists() == true }
                }
            } finally {
                listFile?.delete()
                output?.delete()
            }
        }.start()
    }

    private fun buildAudioFileName(): String {
        val base = sanitizeRecordingName(pendingSaveTitle)
        return if (base.isBlank()) "fabula_zagotovka_${timestamp()}.wav" else "${base}_${timestamp()}.wav"
    }

    private fun sanitizeRecordingName(value: String): String {
        return value.replace(Regex("[\\/:*?\"<>|]"), "_").trim().trimEnd('.')
    }

    private fun buildDescription(): String {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        return buildString {
            append("Название: ").append(pendingSaveTitle).append('\n')
            append("Комментарий: ").append(pendingSaveComment).append('\n')
            append("Дата создания: ").append(date).append('\n')
            append("Фрагменты:").append('\n')
            fragments.forEachIndexed { index, item ->
                append(index + 1).append(". ").append(item.text).append('\n')
            }
        }
    }

    private fun saveRecordingToSaf(tree: Uri, source: File, name: String) {
        val parent = DocumentFile.fromTreeUri(this, tree) ?: throw IllegalStateException("Не удалось открыть папку сохранения")
        if (!parent.canWrite()) throw SecurityException("Папка сохранения недоступна для записи")
        parent.findFile(name)?.let { if (!it.delete()) throw IllegalStateException("Не удалось заменить $name") }
        val destination = parent.createFile("audio/wav", name) ?: throw IllegalStateException("Не удалось создать $name")
        contentResolver.openOutputStream(destination.uri, "w")?.use { out ->
            source.inputStream().use { input -> input.copyTo(out) }
        } ?: throw IllegalStateException("Не удалось записать $name")
    }

    private fun saveDescriptionToSaf(tree: Uri, name: String, text: String) {
        val parent = DocumentFile.fromTreeUri(this, tree) ?: throw IllegalStateException("Не удалось открыть папку сохранения")
        parent.findFile(name)?.let { if (!it.delete()) throw IllegalStateException("Не удалось заменить $name") }
        val destination = parent.createFile("text/plain", name) ?: throw IllegalStateException("Не удалось создать $name")
        contentResolver.openOutputStream(destination.uri, "w")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(text) }
            ?: throw IllegalStateException("Не удалось записать $name")
    }

    private fun chooseFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try {
            startActivityForResult(intent, REQ_FOLDER)
        } catch (e: Exception) {
            Toast.makeText(this, "Не удалось открыть выбор папки сохранения.", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleFolderPicked(data: Intent) {
        val uri = data.data ?: return
        val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        try {
            contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось закрепить права папки", e)
        }
        if (pendingMerge) {
            pendingMerge = false
            val files = fragments.mapNotNull { it.path?.let(::File)?.takeIf(File::exists) }
            if (files.isNotEmpty()) performMerge(uri, files)
        }
    }

    private fun friendlyFolderName(uri: Uri): String {
        val documentId = try { DocumentsContract.getTreeDocumentId(uri) } catch (_: Exception) { null }
        if (documentId.isNullOrBlank()) return queryDisplayName(uri)
        val raw = documentId.substringAfter(':', documentId)
        val parts = raw.split('/').filter { it.isNotBlank() }
        if (parts.isEmpty()) return queryDisplayName(uri)
        val mapped = parts.map { part ->
            when (part.lowercase(Locale.ROOT)) {
                "download" -> "Загрузки"
                "documents" -> "Документы"
                "pictures" -> "Изображения"
                "movies" -> "Видео"
                "music" -> "Музыка"
                "dcim" -> "DCIM"
                else -> part
            }
        }
        return mapped.joinToString("/")
    }

    private fun queryDisplayName(uri: Uri): String {
        return try {
            contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: uri.lastPathSegment ?: "Файл"
        } catch (_: Exception) {
            uri.lastPathSegment ?: "Файл"
        }
    }

    private fun showInstructions() {
        val text = """Режим 1. Записать аудио-заготовку фабулы по тексту
1. Загрузите текст из файла (.txt, .docx) или вставьте из буфера обмена.
2. Отметьте нужные строки или нажмите «Выбрать всё». Приложение разобьёт текст на фрагменты. Нажмите "Записать" у фрагмента и прочитайте его вслух.
3. Кнопки у фрагмента: "Записать" — начать или перезаписать запись, "Прослушать" — проверить запись, "Удалить" — удалить аудиозапись фрагмента. Текст фрагмента при удалении записи остаётся.
4. Когда все нужные фрагменты записаны, нажмите "Склеить и сохранить" внизу экрана.
5. Выберите папку — в неё будет сохранена итоговая стереозапись WAV (44,1 кГц, PCM 16 бит).

Режим 2. Ускорить вашу фабулу ×6, ×144, ×432
1. Выберите исходный файл и настройки подготовки.
2. Нажмите ×6, ×144 или ×432 — обработка начнётся сразу. В ×144 и ×432 автоматически выполняются предыдущие этапы; к основному голосу добавляются тихие ускоренные дорожки.
3. "Прослушать результат" — проверка звука; ползунок меняет громкость прослушивания.
4. Сохраните результат в выбранную папку."""
        val textView = TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(getColor(R.color.fabula_text))
            setPadding(24.dp(), 8.dp(), 24.dp(), 8.dp())
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply { addView(textView) }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Инструкция")
            .setView(scroll)
            .setPositiveButton("Закрыть", null)
            .create()
        dialog.show()
        UiStyles.styleDialogButtons(dialog)
    }

    private fun escapeConcatPath(path: String): String = path.replace("'", "'\\''")
    private fun q(file: File): String = "'${escapeConcatPath(file.absolutePath)}'"
    private fun timestamp(): String = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
    private fun truncate(value: String): String = value

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_AUDIO && grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Без разрешения микрофона запись невозможна.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(amplitudeRunnable)
        recordingIndex = -1
        releaseRecorder()
        stopPlayback()
        recordingFile?.delete()
        super.onDestroy()
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
