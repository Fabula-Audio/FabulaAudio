package com.fabula.audio

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.util.Log
import android.view.Gravity
import android.widget.*
import androidx.core.view.WindowInsetsControllerCompat
import androidx.documentfile.provider.DocumentFile
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

class MainActivity : Activity() {
    companion object {
        private const val TAG_DUR = "FABULA_DUR"
        private const val TAG_SAF = "FABULA_SAF"
        private const val TAG_PROCESS = "FABULA_PROCESS"
        private const val TAG_CMD = "FABULA_CMD"
        private const val TAG_PLAYER = "FABULA_PLAYER"
        private const val TAG_SAVE = "FABULA_SAVE"
        private const val TAG_PLAY = "FABULA_PLAY"
        private const val PREFS = "fabula_storage"
        private const val KEY_SOURCE_LOCATION = "source_location"
        private const val KEY_RESULTS_TREE = "results_tree"
        private const val KEY_CLICK_ENABLED = "prep_click_enabled"
        private const val KEY_NOISE_ENABLED = "prep_noise_enabled"
        private const val KEY_NOISE_NR = "prep_noise_nr"
        private const val KEY_NOISE_NF = "prep_noise_nf"
        private const val KEY_STAGE = "current_stage"
        private const val KEY_VOLUME = "result_volume"
        private const val REQ_PICK_AUDIO = 10
        private const val REQ_PICK_RESULTS = 11
        private const val LIVE_DEBOUNCE_MS = 400L
        private const val LIVE_INDICATOR_MS = 3000L
        private const val LIVE_PRE_ROLL_SEC = 5.0
        private const val LIVE_WINDOW_SEC = 35.0
    }

    private var mainUri: Uri? = null
    private var resultsTreeUri: Uri? = null
    private var currentStage = "6"
    private var currentResultFile: File? = null
    private var currentResultStage = "6"
    private var exiting = false
    private var processing = false
    private var processingCancellation: ProcessingCancellation? = null
    private val workerCancellation = ThreadLocal<ProcessingCancellation>()
    private var player: MediaPlayer? = null
    private var playerFile: File? = null
    private var playerBaseFile: File? = null
    private var playerStage = "prep"
    private var playerWindowStartSec = 0.0
    private var liveGeneration = 0L
    private val liveHandler = Handler(Looper.getMainLooper())
    private var liveRunnable: Runnable? = null
    private var liveIndicatorRunnable: Runnable? = null
    private val stageSignatures = mutableMapOf<String, String>()
    private var pendingSaveTitle: String? = null
    private var pendingSaveDescription: String? = null

    private lateinit var fileName: TextView
    private lateinit var resultsFolderLabel: TextView
    private lateinit var status: TextView
    private lateinit var playButton: Button
    private lateinit var resultButton: Button
    private lateinit var stopButton: Button
    private lateinit var saveButton: Button
    private lateinit var speed6Button: Button
    private lateinit var speed144Button: Button
    private lateinit var speed432Button: Button
    private lateinit var volumeSeek: SeekBar
    private lateinit var volumeLabel: TextView
    private lateinit var clickEnabled: CheckBox
    private lateinit var noiseEnabled: CheckBox
    private lateinit var noiseNrSeek: SeekBar
    private lateinit var noiseNrLabel: TextView
    private lateinit var noiseNfSeek: SeekBar
    private lateinit var noiseNfLabel: TextView
    private lateinit var exitButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fileName = findViewById(R.id.fileName)
        resultsFolderLabel = findViewById(R.id.resultsFolderLabel)
        status = findViewById(R.id.status)
        playButton = findViewById(R.id.playButton)
        resultButton = findViewById(R.id.resultButton)
        stopButton = findViewById(R.id.stopButton)
        saveButton = findViewById(R.id.saveButton)
        speed6Button = findViewById(R.id.speed6Button)
        speed144Button = findViewById(R.id.speed144Button)
        speed432Button = findViewById(R.id.speed432Button)
        volumeSeek = findViewById(R.id.volumeSeek)
        volumeLabel = findViewById(R.id.volumeLabel)
        clickEnabled = findViewById(R.id.clickEnabled)
        noiseEnabled = findViewById(R.id.noiseEnabled)
        noiseNrSeek = findViewById(R.id.noiseNrSeek)
        noiseNrLabel = findViewById(R.id.noiseNrLabel)
        noiseNfSeek = findViewById(R.id.noiseNfSeek)
        noiseNfLabel = findViewById(R.id.noiseNfLabel)
        exitButton = findViewById(R.id.exitButton)

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        resultsTreeUri = prefs.getString(KEY_RESULTS_TREE, null)?.let(Uri::parse)
        currentStage = "6"
        updateResultsFolderLabel()

        clickEnabled.isChecked = prefs.getBoolean(KEY_CLICK_ENABLED, true)
        noiseEnabled.isChecked = prefs.getBoolean(KEY_NOISE_ENABLED, true)
        noiseNrSeek.max = 24
        noiseNrSeek.progress = prefs.getInt(KEY_NOISE_NR, 6).coerceIn(0, 24)
        noiseNfSeek.max = 50
        noiseNfSeek.progress = (prefs.getInt(KEY_NOISE_NF, -30) + 60).coerceIn(0, 50)
        updateNoiseLabels()
        updateNoiseControls()

        findViewById<Button>(R.id.openButton).setOnClickListener { pickAudio() }
        findViewById<Button>(R.id.helpButton).setOnClickListener { showInstructions() }
        findViewById<Button>(R.id.menuButton).setOnClickListener { showHeaderMenu(it) }
        speed6Button.setOnClickListener { chooseStage("6") }
        speed144Button.setOnClickListener { chooseStage("144") }
        speed432Button.setOnClickListener { chooseStage("432") }
        playButton.setOnClickListener { playCurrentStage() }
        resultButton.setOnClickListener { playProcessedResult() }
        stopButton.setOnClickListener {
            val operation = processingCancellation
            if (processing && operation != null) {
                operation.cancel()
                stopButton.isEnabled = false
                status.text = "Отмена обработки…"
            } else stopPlayback()
        }
        saveButton.setOnClickListener { showSaveDialog() }
        exitButton.setOnClickListener { exitApplication() }
        findViewById<Button>(R.id.homeButton).setOnClickListener { finish() }
        UiStyles.styleActionButton(findViewById(R.id.homeButton))

        UpdateManager(this).checkForUpdate()

        clickEnabled.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_CLICK_ENABLED, checked).apply()
            markCurrentStageDirty()
            requestLiveRebuild()
        }
        noiseEnabled.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_NOISE_ENABLED, checked).apply()
            updateNoiseControls()
            markCurrentStageDirty()
            requestLiveRebuild()
        }
        noiseNrSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                prefs.edit().putInt(KEY_NOISE_NR, progress).apply()
                updateNoiseLabels()
                markCurrentStageDirty()
                requestLiveRebuild()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) { requestLiveRebuild() }
        })
        noiseNfSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val value = progress - 60
                prefs.edit().putInt(KEY_NOISE_NF, value).apply()
                updateNoiseLabels()
                markCurrentStageDirty()
                requestLiveRebuild()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) { requestLiveRebuild() }
        })

        volumeSeek.progress = prefs.getInt(KEY_VOLUME, 100).coerceIn(0, 100)
        updateVolumeLabel()
        volumeSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateVolumeLabel()
                setPlayerVolume(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                prefs.edit().putInt(KEY_VOLUME, volumeSeek.progress).apply()
            }
        })

        styleControlButtons()
        styleHeaderButtons()
        UiStyles.styleActionButton(exitButton)
        playButton.isEnabled = false
        resultButton.isEnabled = false
        stopButton.isEnabled = false
        saveButton.isEnabled = false
        selectModeStyle(currentStage)
    }

    private fun showHeaderMenu(anchor: android.view.View) {
        PopupMenu(this, anchor).apply {
            menu.add("Папка сохранения")
            menu.add("О приложении")
            menu.add("Связаться с разработчиком")
            setOnMenuItemClickListener {
                when (it.title) {
                    "Папка сохранения" -> {
                        pickResultsFolder()
                        true
                    }
                    "О приложении" -> {
                        showAboutDialog()
                        true
                    }
                    "Связаться с разработчиком" -> {
                        contactDeveloper()
                        true
                    }
                    else -> false
                }
            }
        }.show()
    }

    private fun contactDeveloper() {
        val uri = Uri.Builder()
            .scheme("mailto")
            .path("fabula.audio@yandex.ru")
            .appendQueryParameter("subject", "Fabula Audio — вопрос/предложение")
            .build()
        try {
            startActivity(Intent(Intent.ACTION_SENDTO, uri))
        } catch (_: Exception) {
            Toast.makeText(this, "Почтовое приложение не найдено.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showAboutDialog() {
        AboutDialog.show(this)
    }

    private fun showInstructions() {
        val text = """FABULA AUDIO — ИНСТРУКЦИЯ ПОЛЬЗОВАТЕЛЯ

ЧТО ДЕЛАЕТ ПРИЛОЖЕНИЕ
Приложение обрабатывает аудиозаписи по методике проекта "Ключ к Единству.".

Убирает щелчки и шумы
с диктофонной записи, затем ускоряет трек в 6 раз. Готовый трек можно
прослушать, отрегулировать по громкости и сохранить в нужную папку.

1. ВЫБОР ФАЙЛОВ
1.1. Нажмите «Выбрать трек» и укажите исходный аудиофайл. Папка, из которой вы
     выбрали файл, запоминается — в следующий раз выбор откроется сразу в ней.

1.2. Выбранный трек начнётся после нажатия  на  «Плей».

2. ПОДГОТОВКА ОРИГИНАЛА
2.1. Нажмите «Плей» — зазвучит исходный трек.
2.2. Прямо во время прослушивания можно менять обработку — звук обновится сразу:
     - «Убрать щелчки» — включение/выключение очистки от щелчков;
     - «Убрать шумы» — включение/выключение шумоподавления;
     - «Стоп» — остановит прослушивание.
2.3. Добейтесь на слух чистого, но естественного звучания оригинала.
     Все положения тумблеров и ползунков запоминаются.

3. ОБРАБОТКА
3.1. Нажмите «×6», «×144» или «×432». Обработка начнётся сразу.
     Для ×144 и ×432 предыдущие этапы выполняются автоматически.
     Основной ×6 сохраняется без приглушения; ускоренные дорожки добавляются на −36 дБ, по настройкам заказчика.
3.2. Текущий этап отображается в строке статуса; дождитесь сообщения о готовности.
3.3. Проверьте готовый результат ×6. Настройки шумоподавления можно включать или
     выключать перед обработкой оригинала.
3.4. Если после обработки изменить настройки подготовки или выбрать другой трек,
     появится предупреждение; кнопки результата останутся активными, но для учёта
     изменений нажмите кнопку режима заново.

4. ПРОСЛУШИВАНИЕ И СОХРАНЕНИЕ РЕЗУЛЬТАТА

4.1. «Прослушать результат» — воспроизведение готового трека. Одновременно звучит
     только один трек: новый запуск глушит предыдущий. «Стоп» — остановит.
4.2. Ползунок «Громкость прослушивания результата перед сохранением» задаёт громкость,
     с которой трек будет сохранён (прослушивание идёт с этой же громкостью).
4.3. «Сохранить результат» — введите название трека (обязательно) и, при желании,
     описание (необязательно). Файл сохранится в папку сохранения.

5. ВЫХОД
Кнопка «Выход» закрывает приложение полностью.

ПАМЯТКА ПО ЧАСТЫМ СИТУАЦИЯМ
- Слишком много щелчков → включите «Убрать щелчки» и прослушайте оригинал снова.
- Результат не там, где ожидали → смените папку через меню шапки
  («Папка сохранения»).
- Кнопки результата доступны, но звук прежний → настройки подготовки изменены
  после обработки: нажмите кнопку режима заново.

КОРОТКИЙ ПУТЬ (БЫСТРЫЙ СТАРТ)
Выбрать трек → Плей → (при необходимости «Убрать щелчки» / «Убрать шумы») →
Стоп → нажать ×6 (обработка начнётся сразу) → дождаться завершения →
Прослушать результат → ползунок громкости → Сохранить результат → название и описание."""
        val textView = TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(getColor(R.color.fabula_text))
            setPadding(24, 8, 24, 8)
        }
        val scroll = ScrollView(this).apply {
            addView(textView)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Инструкция")
            .setView(scroll)
            .setPositiveButton("Закрыть", null)
            .create()
        dialog.show()
        UiStyles.styleDialogButtons(dialog)
    }

    private fun pickAudio() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "audio/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            val lastLocation = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(KEY_SOURCE_LOCATION, null)?.let(Uri::parse)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && lastLocation != null) {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, lastLocation)
            }
        }
        try {
            startActivityForResult(intent, REQ_PICK_AUDIO)
        } catch (e: Exception) {
            Log.e(TAG_SAF, "Не удалось открыть выбор аудиофайла", e)
            status.text = "Не удалось открыть выбор аудиофайла."
        }
    }

    private fun pickResultsFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try {
            startActivityForResult(intent, REQ_PICK_RESULTS)
        } catch (e: Exception) {
            Log.e(TAG_SAF, "Не удалось открыть выбор папки сохранения", e)
            status.text = "Не удалось открыть выбор папки сохранения."
        }
    }

    @Deprecated("Activity Result API kept to preserve the existing project structure")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        val uri = data.data!!
        when (requestCode) {
            REQ_PICK_AUDIO -> handleAudioPicked(uri, data)
            REQ_PICK_RESULTS -> handleResultsFolderPicked(uri, data)
        }
    }

    private fun handleAudioPicked(uri: Uri, data: Intent) {
        try {
            contentResolver.takePersistableUriPermission(uri, data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
            Log.w(TAG_SAF, "Не удалось закрепить право чтения выбранного аудиофайла", e)
        }
        val hadResult = currentResultFile?.exists() == true
        stopPlayback(false)
        cancelLiveRebuilds()
        mainUri = uri
        stageSignatures.clear()
        fileName.text = "Файл: ${displayName(uri)}"
        playButton.isEnabled = true
        resultButton.isEnabled = hadResult
        saveButton.isEnabled = hadResult
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_SOURCE_LOCATION, uri.toString()).apply()
        if (hadResult) {
            showPreparationChangedWarning()
        } else {
            status.text = "Трек выбран. Нажмите «Плей» — воспроизведение не запускается автоматически."
        }
    }

    private fun handleResultsFolderPicked(uri: Uri, data: Intent) {
        val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        try { contentResolver.takePersistableUriPermission(uri, flags) }
        catch (e: Exception) { Log.w(TAG_SAF, "Не удалось закрепить права папки результатов", e) }
        resultsTreeUri = uri
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_RESULTS_TREE, uri.toString()).apply()
        updateResultsFolderLabel()
        val retryTitle = pendingSaveTitle
        val retryDescription = pendingSaveDescription
        pendingSaveTitle = null
        pendingSaveDescription = null
        if (retryTitle != null) {
            status.text = "Папка сохранения выбрана. Повторяю сохранение…"
            saveResult(retryTitle, retryDescription.orEmpty())
        } else {
            status.text = "Папка сохранения выбрана."
        }
    }

    private fun displayName(uri: Uri): String {
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        return try {
            contentResolver.query(uri, projection, null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: uri.lastPathSegment ?: "Аудиофайл"
        } catch (e: Exception) {
            Log.w(TAG_SAF, "Не удалось определить имя документа через query, пробуем DocumentFile", e)
            try {
                DocumentFile.fromTreeUri(this, uri)?.name ?: uri.lastPathSegment ?: "Папка"
            } catch (_: Exception) {
                uri.lastPathSegment ?: "Аудиофайл"
            }
        }
    }

    private fun updateResultsFolderLabel() {
        resultsFolderLabel.text = if (resultsTreeUri == null) "Папка сохранения не выбрана" else "Папка: ${displayName(resultsTreeUri!!)}"
    }

    private fun selectModeStyle(stage: String) {
        applyModeStyle(speed6Button, stage == "6")
        applyModeStyle(speed144Button, stage == "144")
        applyModeStyle(speed432Button, stage == "432")
    }

    private fun styleControlButtons() {
        listOf(
            findViewById<Button>(R.id.openButton),
            playButton, resultButton, stopButton, saveButton,
            speed6Button, speed144Button, speed432Button
        ).forEach { button ->
            UiStyles.styleActionButton(button)
        }
    }

    private fun styleHeaderButtons() {
        listOf(findViewById<Button>(R.id.helpButton), findViewById<Button>(R.id.menuButton)).forEach { button ->
            button.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.fabula_header_button)))
            button.setTextColor(getColor(R.color.fabula_text))
        }
        findViewById<Button>(R.id.helpButton).tooltipText = "Инструкция"
        findViewById<Button>(R.id.menuButton).tooltipText = "Меню"
    }

    private fun applyModeStyle(button: Button, selected: Boolean) {
        if (selected) {
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12f * resources.displayMetrics.density
                setColor(getColor(R.color.fabula_button))
                setStroke(3, getColor(R.color.fabula_mode_border))
            }
            button.background = drawable
            button.setTextColor(getColor(R.color.fabula_button_text))
        } else {
            UiStyles.styleActionButton(button)
        }
    }

    private fun updateNoiseLabels() {
        noiseNrLabel.text = "Уровень подавления, дБ: ${noiseNrSeek.progress}"
        noiseNfLabel.text = "Пол шума, дБ: ${noiseNfSeek.progress - 60}"
    }

    private fun updateNoiseControls() {
        val enabled = noiseEnabled.isChecked
        noiseNrSeek.isEnabled = enabled && !processing
        noiseNfSeek.isEnabled = enabled && !processing
        noiseNrLabel.alpha = if (enabled) 1f else 0.5f
        noiseNfLabel.alpha = if (enabled) 1f else 0.5f
    }

    private fun updateVolumeLabel() {
        volumeLabel.text = "Громкость прослушивания результата перед сохранением: ${volumeSeek.progress}%"
    }

    private fun setPlayerVolume(progress: Int) {
        val gain = progress / 100f
        try { player?.setVolume(gain, gain) } catch (e: Exception) { Log.w(TAG_PLAYER, "Не удалось изменить громкость", e) }
    }

    private fun preparationSignature(): String {
        return "click=${clickEnabled.isChecked};noise=${noiseEnabled.isChecked};nr=${noiseNrSeek.progress};nf=${noiseNfSeek.progress - 60}"
    }

    private fun markCurrentStageDirty() {
        stageSignatures.remove(currentStage)
        if (currentResultFile?.exists() == true) {
            showPreparationChangedWarning()
            resultButton.isEnabled = true
            saveButton.isEnabled = true
        }
    }

    private fun showPreparationChangedWarning() {
        val message = "Настройки подготовки изменены. Для учёта изменений запустите обработку заново."
        status.text = message
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun preparationFilters(): String {
        val filters = mutableListOf<String>()
        if (clickEnabled.isChecked) filters += "adeclick"
        if (noiseEnabled.isChecked) filters += "afftdn=nr=${noiseNrSeek.progress}:nf=${noiseNfSeek.progress - 60}"
        return filters.joinToString(",")
    }

    private fun requestLiveRebuild() {
        val currentMainUri = mainUri
        if (currentMainUri == null || processing || player == null) return
        liveRunnable?.let(liveHandler::removeCallbacks)
        val generation = ++liveGeneration
        val runnable = Runnable { rebuildLivePlayback(generation) }
        liveRunnable = runnable
        liveHandler.postDelayed(runnable, LIVE_DEBOUNCE_MS)
    }

    private fun cancelLiveRebuilds() {
        liveRunnable?.let(liveHandler::removeCallbacks)
        liveRunnable = null
        liveGeneration++
        liveIndicatorRunnable?.let(liveHandler::removeCallbacks)
        liveIndicatorRunnable = null
    }

    private fun rebuildLivePlayback(generation: Long) {
        val baseFile = playerBaseFile ?: playerFile ?: return
        val stage = playerStage
        val oldPlayer = player ?: return
        val relativePositionSec = try { oldPlayer.currentPosition / 1000.0 } catch (_: Exception) { return }
        val absolutePositionSec = playerWindowStartSec + relativePositionSec
        val durationSec = durationOf(baseFile)
        val startSec = if (durationSec < 40.0) 0.0 else maxOf(0.0, absolutePositionSec - LIVE_PRE_ROLL_SEC)
        val windowDuration = if (durationSec < 40.0) durationSec else minOf(LIVE_WINDOW_SEC, durationSec - startSec)
        if (windowDuration <= 0.0) return
        val seekInWindowMs = ((absolutePositionSec - startSec).coerceAtLeast(0.0) * 1000.0).toLong()
        val inputFile = baseFile
        stopPlayback(false)
        status.text = "Подготавливаю изменение…"
        val indicator = Runnable {
            if (generation == liveGeneration) status.text = "Применяю…"
        }
        liveIndicatorRunnable = indicator
        liveHandler.postDelayed(indicator, LIVE_INDICATOR_MS)

        Thread {
            try {
                val out = File(cacheDir, "fabula_live_${generation}.flac")
                val filters = preparationFilters()
                val filterPart = if (filters.isBlank()) "" else " -af ${q(filters)}"
                val cmd = "-y -ss ${fmt(startSec)} -t ${fmt(windowDuration)} -i ${q(inputFile)}$filterPart -c:a flac ${q(out)}"
                val session = executeFfmpeg("Live-$stage", cmd)
                check(ReturnCode.isSuccess(session.returnCode)) { "Ошибка live-пересборки" }
                if (generation != liveGeneration || processing) return@Thread
                runOnUiThread {
                    liveIndicatorRunnable?.let(liveHandler::removeCallbacks)
                    liveIndicatorRunnable = null
                    playerFile = out
                    playerBaseFile = inputFile
                    playerStage = stage
                    playerWindowStartSec = startSec
                    playFile(out, "Прослушивание $stage", seekInWindowMs, inputFile, stage, startSec)
                }
            } catch (e: Exception) {
                Log.e(TAG_PROCESS, "Ошибка live-пересборки", e)
                if (generation == liveGeneration) runOnUiThread {
                    liveIndicatorRunnable?.let(liveHandler::removeCallbacks)
                    liveIndicatorRunnable = null
                    status.text = "Ошибка применения настройки: ${e.message ?: "неизвестная ошибка"}"
                }
            }
        }.start()
    }

    private fun playCurrentStage() {
        if (processing || exiting) return
        if (mainUri == null) { toast("Сначала выберите трек."); return }
        stopPlayback(false)
        processing = true
        setProcessingEnabled(false)
        status.text = "Подготовка оригинала для прослушивания…"
        startProcessing { operation ->
            try {
                val file = ensureStage("prep")
                val duration = durationOf(file)
                Log.i(TAG_PLAY, "mode=prep src=${file.name} dur=${fmt(duration)}")
                runOnUiThread {
                    if (operation.isCancelled) { finishCancelledProcessing(); return@runOnUiThread }
                    processing = false
                    setProcessingEnabled(true)
                    playButton.isEnabled = mainUri != null
                    resultButton.isEnabled = currentResultFile?.exists() == true
                    saveButton.isEnabled = resultButton.isEnabled
                    playFile(file, "Прослушивание оригинала", 0L, file, "prep", 0.0)
                }
            } catch (e: Exception) {
                Log.e(TAG_PROCESS, "Ошибка подготовки оригинала для прослушивания", e)
                runOnUiThread {
                    if (operation.isCancelled) { finishCancelledProcessing(); return@runOnUiThread }
                    processing = false
                    setProcessingEnabled(true)
                    resultButton.isEnabled = currentResultFile?.exists() == true
                    saveButton.isEnabled = resultButton.isEnabled
                    status.text = "Ошибка подготовки прослушивания: ${e.message ?: "неизвестная ошибка"}"
                }
            }
        }
    }

    private fun publishCurrentResult(file: File, stage: String) {
        currentResultFile = file
        currentResultStage = stage
    }

    private fun playProcessedResult() {
        if (processing) return
        val file = currentResultFile
        if (file == null || !file.exists()) {
            toast("Сначала выберите режим ×6, ×144 или ×432.")
            return
        }
        val mode = currentResultStage
        val duration = durationOf(file)
        Log.i(TAG_PLAY, "mode=$mode src=${file.name} dur=${fmt(duration)}")
        stopPlayback(false)
        playFile(file, "Результат ${stageLabel(mode)}", 0L, file, mode, 0.0)
    }

    private fun chooseStage(stage: String) {
        if (processing) return
        if (mainUri == null) { toast("Сначала выберите трек."); return }
        currentStage = stage
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_STAGE, stage).apply()
        selectModeStyle(stage)
        stopPlayback(false)
        saveButton.isEnabled = false
        resultButton.isEnabled = false
        status.text = "Подготовка ${stageLabel(stage)}…"
        processing = true
        setProcessingEnabled(false)
        startProcessing { operation ->
            try {
                val file = ensureStage(stage)
                runOnUiThread {
                    if (operation.isCancelled) { finishCancelledProcessing(); return@runOnUiThread }
                    publishCurrentResult(file, stage)
                    processing = false
                    setProcessingEnabled(true)
                    playButton.isEnabled = true
                    resultButton.isEnabled = true
                    saveButton.isEnabled = true
                    status.text = "${stageLabel(stage)} готов. Нажмите «Прослушать результат»."
                }
            } catch (e: Exception) {
                Log.e(TAG_PROCESS, "Ошибка построения ${stageLabel(stage)}", e)
                runOnUiThread {
                    if (operation.isCancelled) { finishCancelledProcessing(); return@runOnUiThread }
                    processing = false
                    setProcessingEnabled(true)
                    saveButton.isEnabled = false
                    status.text = "Ошибка ${stageLabel(stage)}: ${e.message ?: "неизвестная ошибка"}"
                }
            }
        }
    }

    private fun ensureStage(stage: String): File {
        workerCancellation.get()?.check()
        val uri = mainUri ?: throw IllegalStateException("Трек не выбран")
        val signature = preparationSignature()
        val input = importAudio(uri)

        val clean = File(cacheDir, "fabula_clean.flac")
        if (stage == "prep" || stageSignatures["prep"] != signature || !clean.exists()) {
            runStatus("Подготовка оригинала…")
            buildPreparation(input, clean, signature)
        }
        if (stage == "prep") return clean

        // Последовательность макросов пользователя: ×6 → ×24 → ×3.
        // Сам работающий алгоритм ×6 оставляем прежним.
        val stage6 = File(cacheDir, "fabula_stage6.flac")
        if (stageSignatures["6"] != signature || !stage6.exists()) {
            runStatus("Этап ×6…")
            buildSpeedStage(clean, stage6, "x6", 6, 6)
            stageSignatures["6"] = signature
        }
        if (stage == "6") return stage6

        // Ускоряем отдельные raw-дорожки. Выходной микс нельзя подавать на следующий этап.
        val stage144 = File(cacheDir, "fabula_stage144_raw.flac")
        if (stageSignatures["144"] != signature || !stage144.exists()) {
            runStatus("Этап ×144…")
            buildSbsmsStage(stage6, stage144, 24, 24)
            stageSignatures["144"] = signature
        }
        if (stage == "144") return mixSpeedResult(listOf(stage6, stage144), "144", signature)

        val stage432 = File(cacheDir, "fabula_stage432_raw.flac")
        if (stageSignatures["432"] != signature || !stage432.exists()) {
            runStatus("Этап ×432…")
            buildSbsmsStage(stage144, stage432, 3, 3)
            stageSignatures["432"] = signature
        }
        return mixSpeedResult(listOf(stage6, stage144, stage432), "432", signature)
    }

    private fun buildPreparation(input: File, output: File, signature: String) {
        val filters = preparationFilters()
        val filterPart = if (filters.isBlank()) "" else " -af ${q(filters)}"
        val cmd = "-y -i ${q(input)} ${AudioImport.OUTPUT_OPTIONS}$filterPart -c:a flac ${q(output)}"
        val session = executeFfmpeg("Подготовка оригинала", cmd)
        check(ReturnCode.isSuccess(session.returnCode)) { "Ошибка подготовки оригинала" }
        val inputDuration = durationOf(input)
        val outputDuration = durationOf(output)
        logDuration("Подготовка оригинала", inputDuration, outputDuration)
        check(abs(outputDuration - inputDuration) <= 1.0) { "Подготовка изменила длительность" }
        stageSignatures["prep"] = signature
    }

    private fun buildSpeedStage(input: File, output: File, key: String, tempo: Int, copies: Int) {
        runStatus("$key: чтение длительности…")
        val inputDuration = durationOf(input)

        val fastFile = File(cacheDir, "fabula_fast_${key}.flac")
        // FFmpeg atempo при коэффициенте >2 может пропускать фрагменты сэмплов.
        // Цепочка множителей <=2 сохраняет заданный суммарный темп и уменьшает артефакты.
        val tempoFilter = atempoFilter(tempo)
        val fastCmd = if (tempoFilter.isNotBlank()) {
            "-y -i ${q(input)} -af ${q(tempoFilter)} -c:a flac ${q(fastFile)}"
        } else {
            "-y -i ${q(input)} -c:a flac ${q(fastFile)}"
        }
        val fastSession = executeFfmpeg("$key: ускорение", fastCmd)
        check(ReturnCode.isSuccess(fastSession.returnCode)) { "Ошибка этапа $key (ускорение)" }

        val listFile = File(cacheDir, "fabula_list_${key}.txt")
        listFile.writer().use { writer ->
            for (i in 0 until copies) {
                writer.write("file '${fastFile.absolutePath}'\n")
            }
        }

        val concatCmd = "-y -f concat -safe 0 -i ${q(listFile)} -c:a flac ${q(output)}"
        val concatSession = executeFfmpeg("$key: склейка", concatCmd)

        fastFile.delete()
        listFile.delete()

        check(ReturnCode.isSuccess(concatSession.returnCode)) { "Ошибка этапа $key (склейка)" }

        val fragmentDuration = if (tempo > 1) inputDuration / tempo else inputDuration
        logDuration("$key: fragment", inputDuration, fragmentDuration)
        val outputDuration = durationOf(output)
        val expected = fragmentDuration * copies

        Log.i(TAG_DUR, "$key: in=${fmt(expected)} out=${fmt(outputDuration)}")

        if (abs(outputDuration - expected) > 1.0) {
            Log.w(TAG_DUR, "$key: длина не совпала: expected=${fmt(expected)} out=${fmt(outputDuration)}")
            throw IllegalStateException("Этап $key нарушил ожидаемую длину")
        }
    }

    private fun buildSbsmsStage(input: File, output: File, tempo: Int, copies: Int) {
        val pcm = File.createTempFile("sbsms_input_", ".f32", cacheDir)
        val fast = File.createTempFile("sbsms_fast_", ".f32", cacheDir)
        val encoded = File.createTempFile("sbsms_fast_", ".flac", cacheDir)
        val list = File.createTempFile("sbsms_repeat_", ".txt", cacheDir)
        try {
            val stageName = if (tempo == 24) "×144" else "×432"
            val decode = executeFfmpeg("$stageName: подготовка звука",
                "-y -i ${q(input)} -vn -ar 44100 -ac 2 -c:a pcm_f32le -f f32le ${q(pcm)}")
            check(ReturnCode.isSuccess(decode.returnCode)) { "Не удалось прочитать аудио для ускорения" }
            val frames = pcm.length() / 8L
            check(frames > 0L && pcm.length() % 8L == 0L) { "Некорректный стереозвук" }
            runStatus("$stageName: ускорение SBSMS — 0%. Длинная запись может обрабатываться несколько минут.")
            var lastPercent = -1L
            val fastFrames = SbsmsTempo.process(pcm.absolutePath, fast.absolutePath, tempo) { completed, total ->
                workerCancellation.get()?.check()
                val percent = completed * 100 / total
                if (percent == lastPercent) return@process
                lastPercent = percent
                runStatus("$stageName: ускорение SBSMS — $percent%. Длинная запись может обрабатываться несколько минут.")
            }
            check(fast.length() == fastFrames * 8L) { "Неполный результат ускорения" }
            // Разрешаем только погрешность округления SBSMS, а не потерю секунд аудио.
            val roundingLimit = copies * (2L + frames / 16_777_216L)
            check(abs(fastFrames * copies - frames) <= roundingLimit) { "Ускорение изменило ожидаемую длину" }
            val encode = executeFfmpeg("$stageName: сохранение фрагмента",
                "-y -f f32le -ar 44100 -ac 2 -i ${q(fast)} -c:a flac ${q(encoded)}")
            check(ReturnCode.isSuccess(encode.returnCode)) { "Не удалось сохранить ускоренный фрагмент" }
            list.writeText(List(copies) { "file '${encoded.absolutePath}'" }.joinToString("\n"))
            val lengthFilter = "apad=whole_len=$frames,atrim=end_sample=$frames"
            val concat = executeFfmpeg("$stageName: склейка повторов",
                "-y -f concat -safe 0 -i ${q(list)} -af ${q(lengthFilter)} -c:a flac ${q(output)}")
            check(ReturnCode.isSuccess(concat.returnCode)) { "Не удалось склеить ускоренные фрагменты" }
            logDuration("SBSMS ×$tempo", frames / 44100.0, durationOf(output))
            check(abs(durationOf(output) - frames / 44100.0) <= 0.002) { "Неверная длительность результата" }
        } finally {
            pcm.delete()
            fast.delete()
            encoded.delete()
            list.delete()
        }
    }

    private fun mixSpeedResult(inputs: List<File>, stage: String, signature: String): File {
        require((stage == "144" && inputs.size == 2) || (stage == "432" && inputs.size == 3))
        val key = "$stage:reference-mix-minus36-v1"
        val output = File(cacheDir, "fabula_stage${stage}_mix.flac")
        if (stageSignatures[key] != signature || !output.exists()) {
            runStatus("Сведение дорожек ×$stage…")
            // Эталоны заказчика: исходный ×6 без ослабления + тихие ускоренные слои.
            // normalize=0 сохраняет громкость основной дорожки; raw-слои не меняются.
            val filter = if (stage == "144") SpeedMix.FILTER144 else SpeedMix.FILTER432
            val sources = inputs.joinToString(" ") { "-i ${q(it)}" }
            val session = executeFfmpeg("×$stage: reference mix",
                "-y $sources -filter_complex ${q(filter)} -map ${q("[out]")} -c:a flac ${q(output)}")
            check(ReturnCode.isSuccess(session.returnCode)) { "Не удалось свести дорожки результата" }
            check(abs(durationOf(output) - durationOf(inputs.first())) <= 0.002) { "Сведение изменило длительность" }
            stageSignatures[key] = signature
        }
        return output
    }

    private fun atempoFilter(tempo: Int): String {
        require(tempo >= 1) { "Темп должен быть не меньше 1" }
        if (tempo == 1) return ""

        // Экспериментальная настройка качества для речи: распределяем темп равномерно,
        // чтобы каждый проход WSOLA/atempo работал с близким коэффициентом.
        // Это может уменьшить артефакты по сравнению с цепочками 2,2,2,2,1.5,
        // но НЕ является SBSMS и не гарантирует звучание, идентичное Audacity.
        if (tempo == 6) return "atempo=1.817121,atempo=1.817121,atempo=1.817121"
        if (tempo == 24) return "atempo=1.888175,atempo=1.888175,atempo=1.888175,atempo=1.888175,atempo=1.888175"
        if (tempo == 3) return "atempo=1.732051,atempo=1.732051"

        // Для остальных коэффициентов распределяем ускорение равномерно между проходами.
        // Каждый множитель <= 2; это пробная альтернатива цепочке из множителей 2.0.
        var stages = 1
        var capacity = 2.0
        while (capacity + 1e-9 < tempo.toDouble()) {
            stages++
            capacity *= 2.0
        }
        val factor = Math.pow(tempo.toDouble(), 1.0 / stages)
        return List(stages) { "atempo=${fmt(factor)}" }.joinToString(",")
    }

    private fun importAudio(uri: Uri): File {
        val decoded = File(cacheDir, "input_decoded.flac")
        if (stageSignatures["import"] == uri.toString() && decoded.exists()) return decoded
        stageSignatures.remove("import")
        val compressed = File(cacheDir, "input_main")
        try {
            runStatus("Копирование исходного файла…")
            copyUriToFile(uri, compressed)
            check(compressed.length() > 0) { "Выбранный файл пуст" }
            runStatus("Декодирование аудио…")
            val session = executeFfmpeg("Декодирование аудио",
                "-y -i ${q(compressed)} ${AudioImport.OUTPUT_OPTIONS} -c:a flac ${q(decoded)}")
            check(ReturnCode.isSuccess(session.returnCode) && decoded.length() > 0) {
                "Не удалось декодировать аудио. Проверьте, что файл не повреждён и содержит звуковую дорожку"
            }
            // STREAMINFO gives the exact sample count without Android's media service.
            check(durationOf(decoded) > 0.0) { "В файле нет аудио ненулевой длительности" }
            stageSignatures["import"] = uri.toString()
            return decoded
        } catch (e: Exception) {
            decoded.delete()
            throw e
        } finally {
            compressed.delete()
        }
    }

    private fun executeFfmpeg(stage: String, command: String): com.arthenica.ffmpegkit.FFmpegSession {
        val operation = workerCancellation.get()
        operation?.check()
        Log.i(TAG_CMD, "$stage: $command")
        val finished = CountDownLatch(1)
        val active = AtomicBoolean(true)
        if (processing) runStatus("$stage…")
        var lastSecond = -1L
        val session = FFmpegKit.executeAsync(command, { finished.countDown() }, null, { statistics ->
            val second = (statistics.time / 1000).toLong()
            if (second != lastSecond) {
                lastSecond = second
                runOnUiThread {
                    if (active.get() && processing && processingCancellation?.isCancelled != true) status.text = "$stage: обработано ${second} с аудио…"
                }
            }
        })
        operation?.attach { FFmpegKit.cancel(session.sessionId) }
        try { finished.await() } finally {
            active.set(false)
            operation?.detach()
        }
        operation?.check()
        if (!ReturnCode.isSuccess(session.returnCode)) {
            Log.e(TAG_PROCESS, "$stage: ${session.allLogsAsString}")
        }
        return session
    }

    private fun playFile(file: File, statusText: String, startMs: Long, baseFile: File, stage: String, windowStartSec: Double) {
        stopPlayback(false)
        try {
            val mp = MediaPlayer()
            player = mp
            playerFile = file
            playerBaseFile = baseFile
            playerStage = stage
            playerWindowStartSec = windowStartSec
            mp.setDataSource(file.absolutePath)
            mp.setOnPreparedListener {
                setPlayerVolume(volumeSeek.progress)
                if (startMs > 0) it.seekTo(startMs.toInt().coerceAtLeast(0))
                it.start()
                stopButton.isEnabled = true
                status.text = statusText
            }
            mp.setOnCompletionListener {
                stopPlayback(false)
                status.text = "Воспроизведение завершено."
            }
            mp.setOnErrorListener { _, what, extra ->
                Log.e(TAG_PLAYER, "MediaPlayer error: $what/$extra")
                stopPlayback(false)
                status.text = "Не удалось воспроизвести аудио."
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            Log.e(TAG_PLAYER, "Ошибка запуска MediaPlayer", e)
            stopPlayback(false)
            status.text = "Ошибка воспроизведения: ${e.message ?: "неизвестная ошибка"}"
        }
    }

    private fun stopPlayback(refreshStatus: Boolean = true) {
        val old = player
        player = null
        try { old?.stop() } catch (_: Exception) {}
        try { old?.reset() } catch (_: Exception) {}
        try { old?.release() } catch (_: Exception) {}
        stopButton.isEnabled = false
        if (refreshStatus && !processing) status.text = "Воспроизведение остановлено."
    }

    private fun showSaveDialog() {
        val result = currentResultFile
        if (result == null || !result.exists()) {
            toast("Нет подготовленного этапа.")
            return
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 8, 48, 0)
        }
        val title = EditText(this).apply {
            hint = "Название трека"
            setSingleLine(true)
        }
        val description = EditText(this).apply {
            hint = "Описание"
            minLines = 3
            gravity = android.view.Gravity.TOP
        }
        layout.addView(title)
        layout.addView(description)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Сохранить результат")
            .setView(layout)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сохранить", null)
            .create()
        dialog.setOnShowListener {
            UiStyles.styleDialogButtons(dialog)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val titleText = title.text.toString().trim()
                if (titleText.isBlank()) {
                    title.error = "Введите название трека"
                    title.requestFocus()
                    return@setOnClickListener
                }
                val safeBase = sanitizeBaseName(titleText)
                if (safeBase.isBlank()) {
                    title.error = "Недопустимое название трека"
                    title.requestFocus()
                    return@setOnClickListener
                }
                dialog.dismiss()
                saveResult(titleText, description.text.toString().trim())
            }
        }
        dialog.show()
    }

    private fun showChooseSaveFolderDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Выберите папку сохранения")
            .setMessage("Права на сохранение отсутствуют или папка ещё не выбрана.")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Выбрать") { _, _ -> pickResultsFolder() }
            .create()
        dialog.show()
        UiStyles.styleDialogButtons(dialog)
    }

    private fun saveResult(title: String, description: String) {
        val tree = resultsTreeUri
        val result = currentResultFile
        val mode = currentResultStage
        if (result == null || !result.exists()) {
            toast("Нет готового результата для сохранения.")
            return
        }
        if (tree == null) {
            pendingSaveTitle = title
            pendingSaveDescription = description
            showChooseSaveFolderDialog()
            return
        }

        processing = true
        setProcessingEnabled(false)
        resultButton.isEnabled = false
        saveButton.isEnabled = false
        stopPlayback()
        status.text = "Подготовка сохранения…"
        Thread {
            try {
                ensureWritePermission(tree)
                val duration = durationOf(result)
                Log.i(TAG_SAVE, "mode=$mode src=${result.name} dur=${fmt(duration)}")

                val adjusted = File(cacheDir, "Fabula_result_save_${mode}.flac")
                val baseName = sanitizeBaseName(title)
                val suffix = when (mode) {
                    "6" -> "_x6"
                    "144" -> "_x144"
                    "432" -> "_x432"
                    else -> "_prep"
                }
                val outputName = "$baseName$suffix.flac"
                val descriptionName = "$baseName.txt"
                val volume = volumeSeek.progress / 100.0
                val volumeCmd = "-y -i ${q(result)} -af volume=$volume -c:a flac ${q(adjusted)}"
                val session = executeFfmpeg("Сохранение-громкость", volumeCmd)
                check(ReturnCode.isSuccess(session.returnCode)) { "Ошибка FFmpeg при подготовке файла" }

                val parent = DocumentFile.fromTreeUri(this, tree)
                    ?: throw IllegalStateException("Не удалось открыть папку сохранения через SAF")
                if (!parent.canWrite()) {
                    throw SecurityException("Папка не доступна для записи")
                }

                parent.findFile(outputName)?.let { existing ->
                    if (!existing.delete()) throw SecurityException("Не удалось заменить $outputName")
                }
                val destination = parent.createFile("audio/flac", outputName)
                    ?: throw IllegalStateException("Не удалось создать $outputName в папке сохранения")
                contentResolver.openOutputStream(destination.uri, "w")?.use { out ->
                    adjusted.inputStream().use { input -> input.copyTo(out) }
                } ?: throw IllegalStateException("Не удалось открыть $outputName для записи")

                if (description.isNotBlank()) {
                    parent.findFile(descriptionName)?.let { existing ->
                        if (!existing.delete()) throw SecurityException("Не удалось заменить $descriptionName")
                    }
                    val descriptionDoc = parent.createFile("text/plain", descriptionName)
                        ?: throw IllegalStateException("Не удалось создать $descriptionName")
                    contentResolver.openOutputStream(descriptionDoc.uri, "w")?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                        writer.write(description)
                    } ?: throw IllegalStateException("Не удалось записать $descriptionName")
                }

                Log.i(TAG_SAVE, "ok: ${destination.uri}")
                runOnUiThread {
                    processing = false
                    setProcessingEnabled(true)
                    resultButton.isEnabled = currentResultFile?.exists() == true
                    saveButton.isEnabled = resultButton.isEnabled
                    status.text = "Сохранено: $outputName"
                    toast("Сохранено: $outputName")
                }
            } catch (e: SecurityException) {
                Log.e(TAG_SAVE, "Потеряно право записи: ${e.message}", e)
                runOnUiThread {
                    processing = false
                    setProcessingEnabled(true)
                    pendingSaveTitle = title
                    pendingSaveDescription = description
                    resultButton.isEnabled = currentResultFile?.exists() == true
                    saveButton.isEnabled = resultButton.isEnabled
                    status.text = "Требуется заново выбрать папку сохранения."
                    showChooseSaveFolderDialog()
                }
            } catch (e: Exception) {
                Log.e(TAG_SAVE, "Ошибка сохранения: ${e.message}", e)
                runOnUiThread {
                    processing = false
                    setProcessingEnabled(true)
                    resultButton.isEnabled = currentResultFile?.exists() == true
                    saveButton.isEnabled = resultButton.isEnabled
                    status.text = "Ошибка сохранения: ${e.message ?: "неизвестная ошибка"}"
                    toast("Ошибка сохранения: ${e.message ?: "неизвестная ошибка"}")
                }
            }
        }.start()
    }

    private fun exitApplication() {
        if (exiting) return
        exiting = true
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString(KEY_STAGE, currentStage)
            .putInt(KEY_VOLUME, volumeSeek.progress)
            .apply()
        cancelLiveRebuilds()
        stopPlayback(false)
        try { FFmpegKit.cancel() } catch (e: Exception) { Log.w(TAG_PROCESS, "Не удалось отменить FFmpeg", e) }

        val splash = ImageView(this).apply {
            setImageResource(R.drawable.exit_splash)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.BLACK)
            scaleX = 1.02f
            scaleY = 1.02f
            isClickable = false
            isFocusable = false
        }

        val splashContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(
                splash,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
                )
            )
        }

        setContentView(splashContainer)

        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                        android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.hide(
                android.view.WindowInsets.Type.statusBars().toInt() or
                        android.view.WindowInsets.Type.navigationBars().toInt()
            )
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        Handler(Looper.getMainLooper()).postDelayed({
            finishAffinity()
            android.os.Process.killProcess(android.os.Process.myPid())
        }, 3000L)
    }

    private fun ensureWritePermission(treeUri: Uri) {
        val granted = contentResolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isWritePermission
        }
        if (!granted) throw SecurityException("Нет закреплённого права записи для папки")
    }

    private fun sanitizeBaseName(value: String): String {
        val cleaned = value.replace(Regex("[\\/:*?\"<>|]"), "_").trim().trimEnd('.')
        return cleaned.ifBlank { "Fabula_result" }
    }

    private fun setProcessingEnabled(enabled: Boolean) {
        if (enabled) {
            processingCancellation = null
            stopButton.text = "СТОП"
            stopButton.isEnabled = player != null
        }
        speed6Button.isEnabled = enabled
        speed144Button.isEnabled = enabled
        speed432Button.isEnabled = enabled
        findViewById<Button>(R.id.openButton).isEnabled = enabled
        clickEnabled.isEnabled = enabled
        noiseEnabled.isEnabled = enabled
        noiseNrSeek.isEnabled = enabled && noiseEnabled.isChecked
        noiseNfSeek.isEnabled = enabled && noiseEnabled.isChecked
        playButton.isEnabled = enabled && mainUri != null
        if (!enabled) resultButton.isEnabled = false
    }

    private fun stageLabel(stage: String): String = when (stage) {
        "prep" -> "подготовки оригинала"
        "6" -> "×6"
        "144" -> "×144"
        "432" -> "×432"
        else -> stage
    }

    private fun startProcessing(block: (ProcessingCancellation) -> Unit) {
        val operation = ProcessingCancellation()
        processingCancellation = operation
        stopButton.text = "ОТМЕНИТЬ"
        stopButton.isEnabled = true
        Thread {
            workerCancellation.set(operation)
            try { block(operation) } finally { workerCancellation.remove() }
        }.start()
    }

    private fun finishCancelledProcessing() {
        // A partially overwritten cache entry must never be reused or published.
        stageSignatures.clear()
        currentResultFile = null
        listOf("input_main", "input_decoded.flac", "fabula_clean.flac",
            "fabula_stage6.flac", "fabula_stage144_raw.flac", "fabula_stage432_raw.flac",
            "fabula_stage144_mix.flac", "fabula_stage432_mix.flac",
            "fabula_fast_x6.flac", "fabula_list_x6.txt").forEach { File(cacheDir, it).delete() }
        processing = false
        setProcessingEnabled(true)
        resultButton.isEnabled = false
        saveButton.isEnabled = false
        status.text = "Обработка отменена. Можно выбрать режим заново."
    }

    private fun runStatus(text: String) {
        runOnUiThread {
            if (processingCancellation?.isCancelled != true) status.text = text
        }
    }

    private fun copyUriToFile(uri: Uri, destination: File) {
        contentResolver.openInputStream(uri)?.use { input ->
            destination.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    workerCancellation.get()?.check()
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
            }
        } ?: throw IllegalStateException("Не удалось прочитать выбранный файл")
    }

    private fun durationOf(uri: Uri): Double {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(this, uri)
            (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000.0
        } catch (e: Exception) {
            Log.w(TAG_DUR, "Не удалось определить длительность $uri", e)
            0.0
        } finally { try { retriever.release() } catch (_: Exception) {} }
    }

    private fun durationOf(file: File): Double {
        if (file.extension.equals("flac", ignoreCase = true)) {
            return FlacDuration.read(file)
        }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000.0
        } catch (e: Exception) {
            Log.w(TAG_DUR, "Не удалось определить длительность ${file.name}", e)
            0.0
        } finally { try { retriever.release() } catch (_: Exception) {} }
    }

    private fun logDuration(stage: String, input: Double, output: Double) {
        Log.i(TAG_DUR, "$stage: in=${fmt(input)} out=${fmt(output)}")
    }

    private fun fmt(value: Double): String = String.format(Locale.US, "%.3f", value)

    override fun onDestroy() {
        cancelLiveRebuilds()
        stopPlayback(false)
        super.onDestroy()
    }

    private fun q(file: File) = "\"${file.absolutePath.replace("\\", "\\\\").replace("\"", "\\\"")}\""
    private fun q(value: String) = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
